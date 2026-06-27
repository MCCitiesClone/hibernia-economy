package io.paradaux.chestshop.listeners.preshopcreation;

import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.events.PreShopCreationEvent;
import io.paradaux.chestshop.Security;
import io.paradaux.chestshop.utils.uBlock;
import org.bukkit.Location;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;

import static io.paradaux.chestshop.events.PreShopCreationEvent.CreationOutcome.NO_PERMISSION_FOR_TERRAIN;

/**
 * @author Acrobot
 */
public class TerrainChecker {

    public static void onPreShopCreation(PreShopCreationEvent event) {
        Player player = event.getPlayer();

        if (!Security.canPlaceSign(player, event.getSign())) {
            event.setOutcome(NO_PERMISSION_FOR_TERRAIN);
            return;
        }

        Container connectedContainer = uBlock.findConnectedContainer(event.getSign().getBlock());
        Location containerLocation = (connectedContainer != null ? connectedContainer.getLocation() : null);

        if (!ChestShop.protection().canBuild(player, containerLocation, event.getSign().getLocation())) {
            event.setOutcome(NO_PERMISSION_FOR_TERRAIN);
        }
    }
}
