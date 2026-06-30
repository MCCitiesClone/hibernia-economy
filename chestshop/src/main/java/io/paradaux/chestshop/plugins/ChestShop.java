package io.paradaux.chestshop.plugins;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.paradaux.chestshop.events.protection.ProtectionCheckEvent;
import io.paradaux.chestshop.permission.Permissions;
import io.paradaux.chestshop.services.AccountService;
import io.paradaux.chestshop.signs.ChestShopSign;
import io.paradaux.chestshop.utils.uBlock;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import static io.paradaux.chestshop.utils.ImplementationAdapter.getState;
import static io.paradaux.chestshop.utils.BlockUtil.isSign;

/**
 * ChestShop's own (vanilla) shop-member access protection. {@link #onProtectionCheck} is
 * invoked directly by {@link io.paradaux.chestshop.services.ProtectionService}; this is no
 * longer a Bukkit {@code Listener}. Injected so the membership check goes through
 * {@link AccountService} rather than the static locator (PAR-282).
 *
 * @author Acrobot
 */
@Singleton
public class ChestShop {

    private final AccountService accounts;

    @Inject
    public ChestShop(AccountService accounts) {
        this.accounts = accounts;
    }

    // Invoked directly by ProtectionService (was a NORMAL ProtectionCheckEvent listener).
    public void onProtectionCheck(ProtectionCheckEvent event) {
        if (event.getResult() == Event.Result.DENY || event.isBuiltInProtectionIgnored()) {
            return;
        }

        Block block = event.getBlock();
        Player player = event.getPlayer();

        if (!canAccess(player, block)) {
            event.setResult(Event.Result.DENY);
        }
    }

    public boolean canAccess(Player player, Block block) {
        if (!canBeProtected(block)) {
            return true;
        }

        if (isSign(block)) {
            Sign sign = (Sign) getState(block, false);

            if (!ChestShopSign.isValid(sign)) {
                return true;
            }

            if (!isShopMember(player, sign)) {
                return false;
            }
        }

        if (uBlock.couldBeShopContainer(block)) {
            Sign sign = uBlock.getConnectedSign(block);

            if (sign != null && !isShopMember(player, sign)) {
                return false;
            }
        }

        return true;
    }

    private boolean canBeProtected(Block block) {
        return isSign(block) || uBlock.couldBeShopContainer(block);
    }

    private boolean isShopMember(Player player, Sign sign) {
        return accounts.hasPermission(player, Permissions.OTHER_NAME_ACCESS, sign);
    }
}
