package io.paradaux.chestshop.listeners.block.breaking.attached;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPhysicsEvent;

import static io.paradaux.chestshop.listeners.block.breaking.SignBreak.handlePhysicsBreak;

public class PhysicsBreak implements Listener {

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public static void onSign(BlockPhysicsEvent event) {
        handlePhysicsBreak(event.getBlock());
    }
}
