package io.paradaux.chestshop.listeners.block.breaking;

import com.google.inject.Inject;
import io.paradaux.chestshop.configuration.Properties;
import io.paradaux.chestshop.permission.Permissions;
import io.paradaux.chestshop.signs.ChestShopSign;
import io.paradaux.chestshop.utils.uBlock;
import io.paradaux.hibernia.framework.i18n.Message;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

/**
 * @author Acrobot
 */
public class ChestBreak implements Listener {

    private final Message message;

    @Inject
    public ChestBreak(Message message) {
        this.message = message;
    }

    @EventHandler(ignoreCancelled = true)
    public void onChestBreak(BlockBreakEvent event) {
        if (!canBeBroken(event.getBlock(), event.getPlayer())) {
            event.setCancelled(true);
            message.send(event.getPlayer(), "chestshop.ACCESS_DENIED");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public static void onExplosion(EntityExplodeEvent event) {
        if (event.blockList() == null || !Properties.USE_BUILT_IN_PROTECTION) {
            return;
        }

        for (Block block : event.blockList()) {
            if (!canBeBroken(block, null)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public static void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (!canBeBroken(event.getBlock(), null)) {
            event.setCancelled(true);
        }
    }

    private static boolean canBeBroken(Block block, Player breaker) {
        if (!uBlock.couldBeShopContainer(block) || !Properties.USE_BUILT_IN_PROTECTION) {
            return true;
        }

        Sign shopSign = uBlock.getConnectedSign(block);
        if (breaker != null) {
            return  ChestShopSign.hasPermission(breaker, Permissions.OTHER_NAME_DESTROY, shopSign);
        }
        return shopSign == null;
    }
}
