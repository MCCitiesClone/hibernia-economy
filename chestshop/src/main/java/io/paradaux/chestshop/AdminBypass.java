package io.paradaux.chestshop;

import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player, opt-out admin bypass. Staff normally hold elevated ChestShop
 * permissions ({@code ChestShop.admin}/{@code mod}/…) that let them open and break
 * any shop and trade without being a real customer. {@code /chestshop bypass} lets
 * a staff member voluntarily switch that off so they can <em>play</em> — get
 * charged for trades, lose access to others' shops — and flip it back on later.
 *
 * <p>The whole mechanic rides on one gate: {@link Permission#has} returns
 * {@code false} for the elevated ("staff") nodes below while a player is opted
 * out, so every admin-gated path in the plugin demotes them to a normal player
 * without touching any of those call sites. The toggle command itself is gated by
 * the framework's Bukkit permission check (not {@link Permission#has}), so an
 * opted-out admin can always turn it back on.
 *
 * <p>State is in-memory (resets on restart, re-granting bypass by default — the
 * safe failure mode), mirroring the existing {@code /chestshop access} toggle.
 */
public final class AdminBypass {

    private static final Set<UUID> optedOut = ConcurrentHashMap.newKeySet();

    private AdminBypass() {}

    /** Whether {@code player} has switched their admin bypass OFF (playing as a normal player). */
    public static boolean isDisabled(Player player) {
        return player != null && optedOut.contains(player.getUniqueId());
    }

    /** Flips the bypass state and returns the new value ({@code true} = bypass now disabled). */
    public static boolean toggle(Player player) {
        UUID id = player.getUniqueId();
        if (optedOut.add(id)) {
            return true;
        }
        optedOut.remove(id);
        return false;
    }

    /** Drop a player's state on quit so the set doesn't grow without bound. */
    public static void forget(UUID playerId) {
        optedOut.remove(playerId);
    }

    /**
     * Whether a permission node is one of the elevated "staff" powers that an
     * opted-out admin should lose. Basic player nodes (shop.create/buy/sell,
     * iteminfo, shopinfo, the toggles, the command itself) are never gated.
     */
    public static boolean isGated(String node) {
        String n = node.toLowerCase(Locale.ROOT);
        return n.startsWith("chestshop.admin")      // admin + adminshop
                || n.startsWith("chestshop.mod")
                || n.startsWith("chestshop.name")
                || n.startsWith("chestshop.othername")
                || n.startsWith("chestshop.nofee")
                || n.startsWith("chestshop.notax")
                || n.startsWith("chestshop.nolimit")
                || n.startsWith("chestshop.group");
    }
}
