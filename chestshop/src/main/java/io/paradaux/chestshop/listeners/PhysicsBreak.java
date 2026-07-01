package io.paradaux.chestshop.listeners;

import com.google.inject.Inject;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPhysicsEvent;

public class PhysicsBreak implements Listener {

    private final SignBreak signBreak;

    @Inject
    public PhysicsBreak(SignBreak signBreak) {
        this.signBreak = signBreak;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onSign(BlockPhysicsEvent event) {
        signBreak.handlePhysicsBreak(event.getBlock());
    }
}
