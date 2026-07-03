package io.paradaux.chestshop.listeners;

import com.google.inject.Inject;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPhysicsEvent;

public class PhysicsBreakListener implements Listener {

    private final SignBreakListener signBreak;

    @Inject
    public PhysicsBreakListener(SignBreakListener signBreak) {
        this.signBreak = signBreak;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onSign(BlockPhysicsEvent event) {
        signBreak.handlePhysicsBreak(event.getBlock());
    }
}
