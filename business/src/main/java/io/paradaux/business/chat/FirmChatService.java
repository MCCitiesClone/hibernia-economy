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
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    private CarbonChat carbon;        // null when CarbonChat is absent
    private FirmChatChannel channel;  // null when CarbonChat is absent

    @Inject
    public FirmChatService(FirmService firms, FirmStaffService staff, Business plugin) {
        this.firms = firms;
        this.staff = staff;
        this.log = plugin.getLogger();
    }

    /** Register the firm-chat channel with CarbonChat if present. Call on enable. */
    public void initialise() {
        try {
            this.carbon = CarbonChatProvider.carbonChat();
            this.channel = new FirmChatChannel(this);
            carbon.channelRegistry().register(channel);
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

    /** Online recipients for a sender's active firm (its online staff incl. proprietor). */
    public List<Audience> recipients(UUID senderUuid) {
        Integer firmId = activeFirm.get(senderUuid);
        if (firmId == null) {
            return List.of();
        }
        return new ArrayList<>(staff.getOnlineEmployees(String.valueOf(firmId)));
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
            selectChannel(uuid, carbon.channelRegistry().defaultChannel());
        }
    }

    /** Drop a player's state (e.g. on quit). */
    public void forget(UUID uuid) {
        activeFirm.remove(uuid);
    }

    /** Whether the player belongs to more than one firm (so /firm chat needs an explicit firm). */
    public boolean inMultipleFirms(UUID uuid) {
        return firms.listOwnedOrMemberFirms(uuid).size() > 1;
    }

    private void selectChannel(UUID uuid, ChatChannel target) {
        // userManager().user(uuid) is async; selecting on completion is fine for a toggle.
        carbon.userManager().user(uuid).thenAccept(cp -> cp.selectedChannel(target));
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
