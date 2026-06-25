package io.paradaux.chestshop.listeners.block;

import io.paradaux.chestshop.configuration.Messages;
import io.paradaux.chestshop.signs.ChestShopSign;
import io.paradaux.chestshop.utils.BlockUtil;
import io.paradaux.chestshop.utils.ImplementationAdapter;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;

/**
 * Blocks edits to the back side of a shop sign so the validated front-face
 * payload can't be circumvented. Was the sign half of the version-named
 * {@code adapter/Spigot_1_20}.
 */
public class SignBacksideProtector implements Listener {

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public static void onSignChange(SignChangeEvent event) {
        Block signBlock = event.getBlock();

        if (!BlockUtil.isSign(signBlock)) {
            return;
        }

        if (event.getSide() != Side.FRONT) {
            Sign sign = (Sign) ImplementationAdapter.getState(signBlock, false);
            if (ChestShopSign.isValid(sign)) {
                event.setCancelled(true);
                Messages.CANNOT_CHANGE_SIGN_BACKSIDE.sendWithPrefix(event.getPlayer());
            }
        }
    }
}
