package io.paradaux.chestshop.events;

import org.bukkit.block.Sign;
import org.bukkit.entity.Player;

/**
 * Carrier for a {@code /shopinfo} call or middle click on a sign, passed to
 * {@link io.paradaux.chestshop.services.InfoService#showShopInfo}. Formerly a Bukkit event.
 *
 * @author Phoenix616
 */
public class ShopInfoEvent {

    private Player sender;
    private Sign sign;

    public ShopInfoEvent(Player sender, Sign sign) {
        this.sender = sender;
        this.sign = sign;
    }

    /**
     * @return The Player who initiated the call
     */
    public Player getSender() {
        return sender;
    }

    /**
     * @return The shop sign
     */
    public Sign getSign() {
        return sign;
    }
}
