package io.paradaux.business.chat;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.paradaux.business.Business;
import io.paradaux.business.model.Firm;
import io.paradaux.business.services.FirmService;
import io.paradaux.business.services.FirmStaffService;
import net.draycia.carbon.api.CarbonChat;
import net.draycia.carbon.api.CarbonChatProvider;
import net.draycia.carbon.api.channels.ChatChannel;
import net.kyori.adventure.audience.Audience;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Owns firm-chat state and the single CarbonChat channel (PAR-20). Firm
 * membership and the per-player "active firm" pointer live here (a player can be
 * in several firms, unlike Factions), so {@link FirmChatChannel} can resolve
 * recipients at send time without inflating Carbon's per-message channel loop.
 *
 * <p>CarbonChat is a soft dependency: if it isn't present the feature simply
 * disables itself ({@link #available()} returns false) and the command reports
 * it as unavailable.
 */
@Singleton
public class FirmChatService {

    private final FirmService firms;
    private final FirmStaffService staff;
    private final Logger log;

    /** player → the firm whose chat they're currently in. In-memory (cleared on leave). */
    private final Map<UUID, Integer> activeFirm = new ConcurrentHashMap<>();

    /**
     * Chat moderators with social spy on (PAR-20). A spy receives every firm's chat
     * without being an employee/proprietor and without an active firm of their own —
     * a {@code /socialspy}-style moderation view. In-memory, per session.
     */
    private final Set<UUID> socialSpies = ConcurrentHashMap.newKeySet();

    // Typed as Object — NOT CarbonChat / FirmChatChannel — on purpose. Guice scans
    // this class's declared fields and methods at injector-build time; if any
    // declared member referenced a net.draycia.carbon.* type, that reflection would
    // throw NoClassDefFoundError when CarbonChat isn't installed, failing the whole
    // injector before initialise() ever gets to soft-disable the feature. The actual
    // CarbonChat / FirmChatChannel are stored here at runtime and only ever touched
    // (via casts) inside the guarded method bodies below, which run only when Carbon
    // is present. (Keeps the soft-dependency genuinely soft.)
    private Object carbon;    // CarbonChat when present, else null
    private Object channel;   // FirmChatChannel when present, else null

    @Inject
    public FirmChatService(FirmService firms, FirmStaffService staff, Business plugin) {
        this.firms = firms;
        this.staff = staff;
        this.log = plugin.getLogger();
    }

    /** Register the firm-chat channel with CarbonChat if present. Call on enable. */
    public void initialise() {
        try {
            CarbonChat cc = CarbonChatProvider.carbonChat();
            FirmChatChannel ch = new FirmChatChannel(this);
            cc.channelRegistry().register(ch);
            this.carbon = cc;
            this.channel = ch;
            log.info("Registered the firm chat channel with CarbonChat.");
        } catch (Throwable t) {
            this.carbon = null;
            this.channel = null;
            log.info("CarbonChat unavailable — firm chat disabled (" + t.getClass().getSimpleName() + ").");
        }
    }

    /** Whether firm chat is usable (CarbonChat present + channel registered). */
    public boolean available() {
        return carbon != null && channel != null;
    }

    /** The firm whose chat the player is currently in, if any. */
    public Optional<Integer> activeFirm(UUID uuid) {
        return Optional.ofNullable(activeFirm.get(uuid));
    }

    /**
     * Online recipients for a sender's active firm: its online staff who are also
     * currently tuned into THIS firm's chat. A multi-firm member who has another
     * firm (or no firm) selected is excluded, so messages don't bleed across the
     * firms a player belongs to.
     */
    public List<Audience> recipients(UUID senderUuid) {
        Integer firmId = activeFirm.get(senderUuid);
        if (firmId == null) {
            return List.of();
        }
        // Keyed by UUID so a spy who is also a tuned-in member isn't added twice.
        Map<UUID, Audience> out = new LinkedHashMap<>();
        for (Player employee : staff.getOnlineEmployees(String.valueOf(firmId))) {
            if (firmId.equals(activeFirm.get(employee.getUniqueId()))) {
                out.put(employee.getUniqueId(), employee);
            }
        }
        // Chat moderators with social spy on receive every firm's chat (PAR-20).
        for (UUID spy : socialSpies) {
            if (out.containsKey(spy)) {
                continue;
            }
            Player p = Bukkit.getPlayer(spy);
            if (p != null) {
                out.put(spy, p);
            }
        }
        return new ArrayList<>(out.values());
    }

    /** Toggle social spy for a chat moderator. Returns the new state (true = now spying). */
    public boolean toggleSpy(UUID uuid) {
        if (socialSpies.add(uuid)) {
            return true;
        }
        socialSpies.remove(uuid);
        return false;
    }

    /** Whether the player is currently spying on all firm chat. */
    public boolean isSpy(UUID uuid) {
        return socialSpies.contains(uuid);
    }

    /** Display name of a firm by id, for the spy-view prefix (null if unknown/disbanded). */
    public String firmName(Integer firmId) {
        if (firmId == null) {
            return null;
        }
        Firm firm = firms.getFirmByNameOrId(String.valueOf(firmId));
        return firm == null ? null : firm.getDisplayName();
    }

    /**
     * Enter firm chat: set the active firm (explicit firmId, or the player's sole
     * firm when null) and select the Carbon channel. Returns the firm entered, or
     * empty on failure (not a member / ambiguous / unavailable).
     */
    public Optional<Firm> enterChat(Player player, Integer firmId) {
        if (!available()) {
            return Optional.empty();
        }
        UUID uuid = player.getUniqueId();
        Firm firm = resolveMemberFirm(uuid, firmId);
        if (firm == null) {
            return Optional.empty();
        }
        activeFirm.put(uuid, firm.getFirmId());
        selectChannel(uuid, channel);
        return Optional.of(firm);
    }

    /** Leave firm chat: clear the active firm and switch back to Carbon's default channel. */
    public void leaveChat(Player player) {
        UUID uuid = player.getUniqueId();
        activeFirm.remove(uuid);
        if (available()) {
            selectChannel(uuid, ((CarbonChat) carbon).channelRegistry().defaultChannel());
        }
    }

    /** Drop a player's state (e.g. on quit). */
    public void forget(UUID uuid) {
        activeFirm.remove(uuid);
        socialSpies.remove(uuid);
    }

    /** Whether the player belongs to more than one firm (so /firm chat needs an explicit firm). */
    public boolean inMultipleFirms(UUID uuid) {
        return firms.listOwnedOrMemberFirms(uuid).size() > 1;
    }

    // `target` is an Object (a ChatChannel at runtime) so this method's signature
    // stays free of net.draycia.* — see the carbon/channel field note above.
    private void selectChannel(UUID uuid, Object target) {
        // userManager().user(uuid) is async; selecting on completion is fine for a toggle.
        ((CarbonChat) carbon).userManager().user(uuid)
                .thenAccept(cp -> cp.selectedChannel((ChatChannel) target));
    }

    /** The firm a player may chat in: an explicit firm they're a member of, or their sole firm. */
    private Firm resolveMemberFirm(UUID uuid, Integer firmId) {
        List<Firm> mine = firms.listOwnedOrMemberFirms(uuid);
        if (mine.isEmpty()) {
            return null;
        }
        if (firmId != null) {
            return mine.stream().filter(f -> f.getFirmId() == firmId).findFirst().orElse(null);
        }
        return mine.size() == 1 ? mine.get(0) : null; // ambiguous when in multiple firms
    }
}
