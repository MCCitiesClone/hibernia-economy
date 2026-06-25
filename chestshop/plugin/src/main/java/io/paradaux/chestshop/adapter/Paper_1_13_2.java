package io.paradaux.chestshop.adapter;

import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import static io.paradaux.chestshop.listeners.block.breaking.SignBreak.handlePhysicsBreak;

public class Paper_1_13_2 implements Listener {

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public static void onSign(BlockDestroyEvent event) {
        handlePhysicsBreak(event.getBlock());
    }
}
