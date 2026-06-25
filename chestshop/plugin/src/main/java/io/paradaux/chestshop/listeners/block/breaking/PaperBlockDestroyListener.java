package io.paradaux.chestshop.listeners.block.breaking;

import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Removes a shop when its sign is destroyed by physics via Paper's
 * {@link BlockDestroyEvent} (the Paper-native counterpart to the Spigot
 * {@code BlockPhysicsEvent} path handled by {@link PhysicsBreak}). Was the
 * version-named {@code adapter/Paper_1_13_2}.
 */
public class PaperBlockDestroyListener implements Listener {

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public static void onSign(BlockDestroyEvent event) {
        SignBreak.handlePhysicsBreak(event.getBlock());
    }
}
