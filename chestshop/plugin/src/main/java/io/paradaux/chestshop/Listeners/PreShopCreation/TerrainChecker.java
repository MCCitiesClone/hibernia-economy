package io.paradaux.chestshop.Listeners.PreShopCreation;

import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.Events.PreShopCreationEvent;
import io.paradaux.chestshop.Events.Protection.BuildPermissionEvent;
import io.paradaux.chestshop.Security;
import io.paradaux.chestshop.Utils.uBlock;
import org.bukkit.Location;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import static io.paradaux.chestshop.Events.PreShopCreationEvent.CreationOutcome.NO_PERMISSION_FOR_TERRAIN;

/**
 * @author Acrobot
 */
public class TerrainChecker implements Listener {

    @EventHandler
    public static void onPreShopCreation(PreShopCreationEvent event) {
        Player player = event.getPlayer();

        if (!Security.canPlaceSign(player, event.getSign())) {
            event.setOutcome(NO_PERMISSION_FOR_TERRAIN);
            return;
        }

        Container connectedContainer = uBlock.findConnectedContainer(event.getSign().getBlock());
        Location containerLocation = (connectedContainer != null ? connectedContainer.getLocation() : null);

        BuildPermissionEvent bEvent = new BuildPermissionEvent(player, containerLocation, event.getSign().getLocation());
        ChestShop.callEvent(bEvent);

        if (!bEvent.isAllowed()) {
            event.setOutcome(NO_PERMISSION_FOR_TERRAIN);
        }

    }
}
