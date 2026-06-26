package io.paradaux.chestshop.events;

import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;

/**
 * The carrier for a removed shop, passed to the post-removal reactions run by
 * {@link io.paradaux.chestshop.services.ShopService#onDestroyed}. Formerly a Bukkit
 * event; those reactions (issue the removal refund, log, market deactivation) are now
 * invoked directly as ordered service steps.
 *
 * @author Acrobot
 */
public class ShopDestroyedEvent {

    private final Player destroyer;

    private final Sign sign;
    private final Container container;

    @Deprecated
    public ShopDestroyedEvent(@Nullable Player destroyer, Sign sign, @Nullable Chest chest) {
        this(destroyer, sign, (Container) chest);
    }

    public ShopDestroyedEvent(@Nullable Player destroyer, Sign sign, @Nullable Container container) {
        this.destroyer = destroyer;
        this.sign = sign;
        this.container = container;
    }

    /**
     * @return Shop's destroyer
     */
    @Nullable public Player getDestroyer() {
        return destroyer;
    }

    /**
     * @return Shop's chest
     */
    @Nullable public Container getContainer() {
        return container;
    }

    /**
     * @deprecated Use {@link #getContainer()}
     */
    @Deprecated
    @Nullable public Chest getChest() {
        return container instanceof Chest ? (Chest) container : null;
    }

    /**
     * @return Shop's sign
     */
    public Sign getSign() {
        return sign;
    }
}
