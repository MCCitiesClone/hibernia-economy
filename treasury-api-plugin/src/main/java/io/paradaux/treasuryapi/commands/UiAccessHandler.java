package io.paradaux.treasuryapi.commands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.paradaux.hibernia.framework.i18n.Message;
import io.paradaux.treasuryapi.TreasuryAPI;
import io.paradaux.treasuryapi.mappers.ExplorerUiMapper;
import io.paradaux.treasuryapi.services.KeycloakAdminClient;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Backs the explorer-UI access commands: {@code /treasuryapi ui user
 * add|remove|list} (role grants) and {@code /treasuryapi ui link} (account
 * linking). Roles are stored in {@code explorer_role}; linking redeems a code
 * issued by the web UI, writes {@code explorer_identity}, and (if configured)
 * sets the player's {@code minecraft_uuid} attribute in Keycloak.
 */
@Singleton
public class UiAccessHandler {

    private static final Set<String> VALID_ROLES = Set.of("admin", "government");

    private final ExplorerUiMapper mapper;
    private final KeycloakAdminClient keycloak;
    private final Message message;
    private final TreasuryAPI plugin;
    private final com.google.inject.Injector injector;

    @Inject
    public UiAccessHandler(ExplorerUiMapper mapper, KeycloakAdminClient keycloak,
                           Message message, TreasuryAPI plugin, com.google.inject.Injector injector) {
        this.mapper = mapper;
        this.keycloak = keycloak;
        this.message = message;
        this.plugin = plugin;
        this.injector = injector;
    }

    /**
     * On-demand self-sync: reconcile the calling player's LuckPerms-sourced explorer
     * group memberships now, instead of waiting for the cron. Resolved lazily through
     * the injector and guarded on LuckPerms being installed, since the reconciliation
     * task (and its LuckPerms dependency) is only bound when LuckPerms is present.
     */
    public void doSelfSync(Player player) {
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
            message.send(player, "treasuryapi.ui.sync.unavailable");
            return;
        }
        try {
            injector.getInstance(io.paradaux.treasuryapi.tasks.GroupReconciliationTask.class)
                    .reconcilePlayer(player.getUniqueId());
            message.send(player, "treasuryapi.ui.sync.done");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Self-sync failed for " + player.getUniqueId(), e);
            message.send(player, "treasuryapi.ui.sync.failed");
        }
    }

    public void doUserAdd(CommandSender sender, String role, String playerName) {
        String r = role.toLowerCase(Locale.ROOT);
        if (!VALID_ROLES.contains(r)) {
            message.send(sender, "treasuryapi.ui.user.invalid-role", "role", role);
            return;
        }
        UUID target = resolveUuid(playerName);
        UUID by = (sender instanceof Player p) ? p.getUniqueId() : null;
        mapper.addRole(target, r, by);
        message.send(sender, "treasuryapi.ui.user.added", "role", r, "player", playerName);
    }

    public void doUserRemove(CommandSender sender, String role, String playerName) {
        String r = role.toLowerCase(Locale.ROOT);
        UUID target = resolveUuid(playerName);
        int removed = mapper.removeRole(target, r);
        if (removed > 0) {
            message.send(sender, "treasuryapi.ui.user.removed", "role", r, "player", playerName);
        } else {
            message.send(sender, "treasuryapi.ui.user.not-granted", "role", r, "player", playerName);
        }
    }

    public void doUserList(CommandSender sender, String playerName) {
        UUID target = resolveUuid(playerName);
        List<String> roles = mapper.listRoles(target);
        String display = roles.isEmpty() ? "player (no elevated roles)" : String.join(", ", roles);
        message.send(sender, "treasuryapi.ui.user.list", "player", playerName, "roles", display);
    }

    public void doLink(Player sender, String code) {
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        String sub = mapper.findValidLinkSub(normalized);
        if (sub == null) {
            message.send(sender, "treasuryapi.ui.link.invalid");
            return;
        }

        mapper.upsertIdentity(sub, sender.getUniqueId(), sender.getName(), "in-game:" + sender.getName());
        mapper.deleteLinkCode(normalized);

        if (keycloak.isEnabled()) {
            try {
                keycloak.setMinecraftAttributes(sub, sender.getUniqueId(), sender.getName());
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Keycloak attribute update failed for sub=" + sub, e);
                // Linked locally; the next login resolves via the link table. Tell the user it worked.
                message.send(sender, "treasuryapi.ui.link.partial");
                return;
            }
        }
        message.send(sender, "treasuryapi.ui.link.success");
    }

    @SuppressWarnings("deprecation") // offline-safe name→UUID; blocking call runs on an async command thread
    private UUID resolveUuid(String name) {
        return Bukkit.getOfflinePlayer(name).getUniqueId();
    }
}
