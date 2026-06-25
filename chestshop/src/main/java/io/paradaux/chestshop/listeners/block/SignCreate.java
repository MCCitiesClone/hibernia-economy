package io.paradaux.chestshop.listeners.block;

import io.paradaux.chestshop.utils.BlockUtil;
import io.paradaux.chestshop.utils.ImplementationAdapter;
import io.paradaux.chestshop.utils.StringUtil;
import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.events.PreShopCreationEvent;
import io.paradaux.chestshop.events.ShopCreatedEvent;
import io.paradaux.chestshop.events.SignValidationEvent;
import io.paradaux.chestshop.listeners.block.breaking.SignBreak;
import io.paradaux.chestshop.signs.ChestShopSign;
import io.paradaux.chestshop.players.NameManager;
import io.paradaux.chestshop.utils.uBlock;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;

import static io.paradaux.chestshop.Permission.OTHER_NAME_DESTROY;

/**
 * @author Acrobot
 */
public class SignCreate implements Listener {

    @EventHandler(ignoreCancelled = true)
    public static void onSignChange(SignChangeEvent event) {
        Block signBlock = event.getBlock();

        if (!BlockUtil.isSign(signBlock)) {
            return;
        }

        Sign sign = (Sign) ImplementationAdapter.getState(signBlock, false);

        if (ChestShopSign.isValid(sign) && !ChestShopSign.canAccess(event.getPlayer(), sign)) {
            // There was already a shop here, but the player does not have permission to change it
            event.setCancelled(true);
            sign.update();
            ChestShop.logDebug("Shop sign creation at " + sign.getLocation() + " by " + event.getPlayer().getName() + " was cancelled as there already was a shop here and the player did not have permission to change it");
            return;
        }

        if (ChestShopSign.isValid(event.getLines()) && !NameManager.canUseName(event.getPlayer(), OTHER_NAME_DESTROY, ChestShopSign.getOwner(event.getLines()))) {
            event.setCancelled(true);
            sign.update();
            ChestShop.logDebug("Shop sign creation at " + sign.getLocation() + " by " + event.getPlayer().getName() + " was cancelled as they weren't able to create a shop for the account '" + ChestShopSign.getOwner(event.getLines()) + "'");
            return;
        }

        String[] lines = StringUtil.stripColourCodes(event.getLines());

        SignValidationEvent signValidationEvent = new SignValidationEvent(lines);
        ChestShop.callEvent(signValidationEvent);

        if (!signValidationEvent.isValid()) {
            // Check if a valid shop already existed previously
            if (ChestShopSign.isValid(sign)) {
                SignBreak.sendShopDestroyedEvent(sign, event.getPlayer());
            }
            return;
        }

        PreShopCreationEvent preEvent = new PreShopCreationEvent(event.getPlayer(), sign, lines);
        ChestShop.callEvent(preEvent);

        if (preEvent.getOutcome().shouldBreakSign()) {
            event.setCancelled(true);
            signBlock.breakNaturally();
            ChestShop.logDebug("Shop sign creation at " + sign.getLocation() + " by " + event.getPlayer().getName() + " was cancelled (creation outcome: " + preEvent.getOutcome() + ") and the sign broken");
            return;
        }

        for (byte i = 0; i < preEvent.getSignLines().length && i < 4; ++i) {
            event.setLine(i, preEvent.getSignLine(i));
        }

        if (preEvent.isCancelled()) {
            ChestShop.logDebug("Shop sign creation at " + sign.getLocation() + " by " + event.getPlayer().getName() + " was cancelled (creation outcome: " + preEvent.getOutcome() + ") and sign lines were set to " + String.join(", ", preEvent.getSignLines()));
            return;
        }

        ShopCreatedEvent postEvent = new ShopCreatedEvent(preEvent.getPlayer(), preEvent.getSign(), uBlock.findConnectedContainer(preEvent.getSign()), preEvent.getSignLines(), preEvent.getOwnerAccount());
        ChestShop.callEvent(postEvent);
    }
}
