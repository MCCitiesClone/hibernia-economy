package io.paradaux.chestshop.services;

import com.google.inject.Singleton;
import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.configuration.Properties;
import io.paradaux.treasury.api.TreasuryApi;
import org.bukkit.ChatColor;

import java.math.BigDecimal;
import java.util.logging.Level;

/**
 * The internal economy API: ChestShop's single point of contact with the Treasury
 * ledger ({@link TreasuryApi}). Services call these methods directly instead of
 * firing the old internal {@code Currency*Event}s and routing them through
 * {@code TreasuryListener} — the first step of replacing the economy event bus with
 * a treasury-api-style service boundary.
 *
 * <p>The live {@link TreasuryApi} handle is {@linkplain #bind bound} once Treasury is
 * resolved at enable (from {@code TreasuryListener.prepareListener}); ChestShop
 * requires Treasury, so by the time any economy call runs the handle is set. The
 * genuine cross-plugin event integration points (AccountQuery/AccountAccess) stay as
 * events. Money-moving operations (deposit/withdraw/transfer) move here in later steps.
 */
@Singleton
public class EconomyService {

    private volatile TreasuryApi treasury;

    /** Wire the resolved Treasury handle in once it's available (enable time). */
    public void bind(TreasuryApi treasury) {
        this.treasury = treasury;
    }

    /** Render a money amount for display through Treasury, honouring STRIP_PRICE_COLORS. */
    public String format(BigDecimal amount) {
        TreasuryApi t = treasury;
        if (t == null) {
            return amount.toPlainString();
        }
        try {
            String formatted = t.formatAmount(amount);
            return Properties.STRIP_PRICE_COLORS ? ChatColor.stripColor(formatted) : formatted;
        } catch (Exception e) {
            ChestShop.getBukkitLogger().log(Level.WARNING, "Treasury: Could not format amount " + amount, e);
            return amount.toPlainString();
        }
    }
}
