package io.paradaux.chestshop.listeners.preshopcreation;

import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.database.Account;
import io.paradaux.chestshop.events.PreShopCreationEvent;
import io.paradaux.chestshop.Permission;
import io.paradaux.chestshop.signs.ChestShopSign;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.logging.Level;

import static io.paradaux.chestshop.Permission.OTHER_NAME_CREATE;
import static io.paradaux.chestshop.signs.ChestShopSign.NAME_LINE;
import static io.paradaux.chestshop.events.PreShopCreationEvent.CreationOutcome.UNKNOWN_PLAYER;

/**
 * @author Acrobot
 */
public class NameChecker implements Listener {

    @EventHandler(priority = EventPriority.LOW)
    public static void onPreShopCreation(PreShopCreationEvent event) {
        handleEvent(event);
    }

    /**
     * ignoreCancelled = true prevents this second pass from overriding a rejection
     * that was already issued during the LOW-priority pass (e.g. no CHESTSHOP permission,
     * business account not found, Treasury not loaded).
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public static void onPreShopCreationHighest(PreShopCreationEvent event) {
        handleEvent(event);
    }

    private static void handleEvent(PreShopCreationEvent event) {
        String name = ChestShopSign.getOwner(event.getSignLines());
        Player player = event.getPlayer();

        // Business accounts have a dedicated validation path that produces specific
        // error messages and enforces the CHESTSHOP firm permission.
        if (ChestShopSign.isBusinessAccount(name)) {
            handleBusinessAccount(event, player, name);
            return;
        }

        // --- Regular (player) account flow ---
        Account account = event.getOwnerAccount();
        if (account == null || !account.getShortName().equalsIgnoreCase(name)) {
            account = null;
            try {
                if (name.isEmpty() || !ChestShop.accounts().canUseName(player, OTHER_NAME_CREATE, name)) {
                    account = ChestShop.accounts().getOrCreateAccount(player);
                } else {
                    account = ChestShop.accounts().resolveAccount(name);
                    if (account == null) {
                        Player otherPlayer = ChestShop.getBukkitServer().getPlayer(name);
                        try {
                            if (otherPlayer != null) {
                                account = ChestShop.accounts().getOrCreateAccount(otherPlayer);
                            } else {
                                account = ChestShop.accounts().getOrCreateAccount(ChestShop.getBukkitServer().getOfflinePlayer(name));
                            }
                        } catch (IllegalArgumentException e) {
                            event.getPlayer().sendMessage(e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                ChestShop.getBukkitLogger().log(Level.SEVERE, "Error while trying to check account for name " + name + " with player " + player.getName(), e);
            }
        }
        event.setOwnerAccount(account);
        if (account != null) {
            event.setSignLine(NAME_LINE, account.getShortName());
        } else {
            event.setSignLine(NAME_LINE, "");
            event.setOutcome(UNKNOWN_PLAYER);
        }
    }

    /**
     * Validates and resolves a business account (B:&lt;base36&gt;) sign name.
     *
     * <p>Checks in order:
     * <ol>
     *   <li>Treasury plugin is available (required for business accounts).</li>
     *   <li>The Treasury account with the encoded ID actually exists.</li>
     *   <li>The creating player holds the {@code CHESTSHOP} firm permission for that
     *       account (via {@code AccountService.canAccess} → EconomyService → Business API),
     *       unless they have the {@code ChestShop.admin} node.</li>
     * </ol>
     *
     * <p>A specific error message is sent for each failure case so the player knows
     * exactly why the shop could not be created.
     */
    private static void handleBusinessAccount(PreShopCreationEvent event, Player player, String name) {
        // Already correctly resolved in a previous handler pass — nothing to do.
        Account existing = event.getOwnerAccount();
        if (existing != null && existing.getShortName().equalsIgnoreCase(name)) {
            return;
        }

        if (Bukkit.getPluginManager().getPlugin("Treasury") == null) {
            ChestShop.message().send(player, "chestshop.TREASURY_REQUIRED");
            event.setSignLine(NAME_LINE, "");
            event.setOutcome(UNKNOWN_PLAYER);
            return;
        }

        // Resolve the Treasury account for the encoded firm account ID.
        Account account = ChestShop.accounts().resolveAccount(name);

        if (account == null) {
            ChestShop.message().send(player, "chestshop.BUSINESS_ACCOUNT_NOT_FOUND");
            event.setSignLine(NAME_LINE, "");
            event.setOutcome(UNKNOWN_PLAYER);
            return;
        }

        // ChestShop admins bypass firm-level permission checks.
        if (!Permission.has(player, Permission.ADMIN)) {
            // AccountService.canAccess checks the CHESTSHOP firm permission via the
            // Business API (or falls back to Treasury membership) through EconomyService.
            if (!ChestShop.accounts().canAccess(player, account)) {
                ChestShop.message().send(player, "chestshop.BUSINESS_NO_CHESTSHOP_PERMISSION");
                event.setSignLine(NAME_LINE, "");
                event.setOutcome(UNKNOWN_PLAYER);
                return;
            }
        }

        event.setOwnerAccount(account);
        int accountId = ChestShopSign.getBusinessAccountId(account.getShortName());
        event.setSignLine(NAME_LINE, ChestShopSign.businessAccountSignName(accountId));
    }
}
