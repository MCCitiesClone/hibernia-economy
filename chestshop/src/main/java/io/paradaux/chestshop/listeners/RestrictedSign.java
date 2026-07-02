package io.paradaux.chestshop.listeners;

import io.paradaux.chestshop.services.AdminBypass;
import io.paradaux.chestshop.services.ChestShopSign;
import com.google.inject.Inject;
import io.paradaux.chestshop.utils.BlockUtil;
import io.paradaux.chestshop.model.PreTransactionContext;
import io.paradaux.chestshop.utils.Permissions;
import io.paradaux.chestshop.services.AccountService;
import io.paradaux.hibernia.framework.i18n.Message;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;

import static io.paradaux.chestshop.utils.ImplementationAdapter.getState;
import static io.paradaux.chestshop.model.PreTransactionContext.TransactionOutcome.SHOP_IS_RESTRICTED;
import static io.paradaux.chestshop.utils.Permissions.ADMIN;

/**
 * @author Acrobot
 */
public class RestrictedSign implements Listener {
    private static final BlockFace[] SIGN_CONNECTION_FACES = {BlockFace.SELF, BlockFace.UP, BlockFace.EAST, BlockFace.WEST, BlockFace.NORTH, BlockFace.SOUTH};

    private final Message message;
    private final AccountService accounts;
    private final ChestShopSign chestShopSign;

    private final AdminBypass adminBypass;

    @Inject
    public RestrictedSign(Message message, AccountService accounts, ChestShopSign chestShopSign, AdminBypass adminBypass) {
        this.adminBypass = adminBypass;
        this.message = message;
        this.accounts = accounts;
        this.chestShopSign = chestShopSign;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockDestroy(BlockBreakEvent event) {
        Block destroyed = event.getBlock();
        Sign attachedRestrictedSign = getRestrictedSign(destroyed.getLocation());

        if (attachedRestrictedSign == null) {
            return;
        }

        if (!canDestroy(event.getPlayer(), attachedRestrictedSign)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        String[] lines = event.getLines();
        Player player = event.getPlayer();

        if (isRestricted(lines)) {
            if (!hasPermission(player, lines)) {
                message.send(player, "chestshop.ACCESS_DENIED");
                dropSignAndCancelEvent(event);
                return;
            }
            Block connectedSign = event.getBlock().getRelative(BlockFace.DOWN);

            if (!adminBypass.has(player, ADMIN) || !chestShopSign.isValid(connectedSign)) {
                dropSignAndCancelEvent(event);
                return;
            }

            Sign sign = (Sign) getState(connectedSign, false);

            if (!accounts.hasPermission(player, Permissions.OTHER_NAME_DESTROY, sign)) {
                dropSignAndCancelEvent(event);
                return;
            }

            message.send(player, "chestshop.RESTRICTED_SIGN_CREATED");
        }
    }

    public void onPreTransaction(PreTransactionContext event) {
        if (event.isCancelled()) {
            return;
        }

        Sign sign = event.getSign();

        if (isRestrictedShop(sign) && !canAccess(sign, event.getClient())) {
            event.setCancelled(SHOP_IS_RESTRICTED);
        }
    }

    public static Sign getRestrictedSign(Location location) {
        Block currentBlock = location.getBlock();

        if (BlockUtil.isSign(currentBlock)) {
            Sign sign = (Sign) getState(currentBlock, false);

            if (isRestricted(sign)) {
                return sign;
            } else {
                return null;
            }
        }

        for (BlockFace face : SIGN_CONNECTION_FACES) {
            Block relative = currentBlock.getRelative(face);

            if (!BlockUtil.isSign(relative)) {
                continue;
            }

            Sign sign = (Sign) getState(relative, false);

            if (!BlockUtil.getAttachedBlock(sign).equals(currentBlock)) {
                continue;
            }

            if (isRestricted(sign)) {
                return sign;
            }
        }

        return null; //No sign found
    }

    public static boolean isRestrictedShop(Sign sign) {
        Block blockUp = sign.getBlock().getRelative(BlockFace.UP);
        return BlockUtil.isSign(blockUp) && isRestricted(((Sign) getState(blockUp, false)).getLines());
    }

    public static boolean isRestricted(String[] lines) {
        return lines[0].equalsIgnoreCase("[restricted]");
    }

    public static boolean isRestricted(Sign sign) {
        return isRestricted(sign.getLines());
    }

    public boolean canAccess(Sign sign, Player player) {
        Block blockUp = sign.getBlock().getRelative(BlockFace.UP);
        return !BlockUtil.isSign(blockUp) || hasPermission(player, ((Sign) getState(blockUp, false)).getLines());
    }

    public boolean canDestroy(Player player, Sign sign) {
        Sign shopSign = getAssociatedSign(sign);
        return accounts.hasPermission(player, Permissions.OTHER_NAME_DESTROY, shopSign);
    }

    public static Sign getAssociatedSign(Sign restricted) {
        Block down = restricted.getBlock().getRelative(BlockFace.DOWN);
        return BlockUtil.isSign(down) ? (Sign) getState(down, false) : null;
    }

    public boolean hasPermission(Player p, String[] lines) {
        if (adminBypass.has(p, ADMIN)) {
            return true;
        }

        for (String line : lines) {
            if (adminBypass.has(p, Permissions.GROUP + line)) {
                return true;
            }
        }
        return false;
    }

    private static void dropSignAndCancelEvent(SignChangeEvent event) {
        event.getBlock().breakNaturally();
        event.setCancelled(true);
    }
}
