package io.paradaux.chestshop.listeners.preshopcreation;

import io.paradaux.chestshop.events.PreShopCreationEvent;
import io.paradaux.chestshop.Permission;
import io.paradaux.chestshop.Security;
import io.paradaux.chestshop.signs.ChestShopSign;
import io.paradaux.chestshop.utils.uBlock;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import static io.paradaux.chestshop.events.PreShopCreationEvent.CreationOutcome.NO_CHEST;
import static io.paradaux.chestshop.events.PreShopCreationEvent.CreationOutcome.NO_PERMISSION_FOR_CHEST;
import static io.paradaux.chestshop.Permission.ADMIN;

/**
 * @author Acrobot
 */
public class ChestChecker implements Listener {

    @EventHandler(priority = EventPriority.LOW)
    public static void onPreShopCreation(PreShopCreationEvent event) {
        String nameLine = ChestShopSign.getOwner(event.getSignLines());

        Container connectedContainer = uBlock.findConnectedContainer(event.getSign().getBlock());

        if (connectedContainer == null) {
            if (!ChestShopSign.isAdminShop(nameLine)) {
                event.setOutcome(NO_CHEST);
            }
            return;
        }

        Player player = event.getPlayer();

        if (Permission.has(player, ADMIN)) {
            return;
        }

        if (!Security.canAccess(player, connectedContainer.getBlock())) {
            event.setOutcome(NO_PERMISSION_FOR_CHEST);
        }
    }
}
