package io.paradaux.chestshop.integration;

import io.paradaux.chestshop.utils.BlockUtil;
import io.paradaux.chestshop.services.ShopBlockService;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.paradaux.chestshop.model.ProtectionCheck;
import io.paradaux.chestshop.utils.Permissions;
import io.paradaux.chestshop.services.AccountService;
import io.paradaux.chestshop.services.ChestShopSign;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import static io.paradaux.chestshop.utils.BlockUtil.getState;
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
public class VanillaShopProtection {

    private final AccountService accounts;
    private final ChestShopSign chestShopSign;
    private final ShopBlockService shopBlockService;

    @Inject
    public VanillaShopProtection(AccountService accounts, ChestShopSign chestShopSign, ShopBlockService shopBlockService) {
        this.accounts = accounts;
        this.chestShopSign = chestShopSign;
        this.shopBlockService = shopBlockService;
    }

    // Invoked directly by ProtectionService (was a NORMAL ProtectionCheck listener).
    public void onProtectionCheck(ProtectionCheck event) {
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

            if (!chestShopSign.isValid(sign)) {
                return true;
            }

            if (!isShopMember(player, sign)) {
                return false;
            }
        }

        if (shopBlockService.couldBeShopContainer(block)) {
            Sign sign = shopBlockService.getConnectedSign(block);

            if (sign != null && !isShopMember(player, sign)) {
                return false;
            }
        }

        return true;
    }

    private boolean canBeProtected(Block block) {
        return isSign(block) || shopBlockService.couldBeShopContainer(block);
    }

    private boolean isShopMember(Player player, Sign sign) {
        return accounts.hasPermission(player, Permissions.OTHER_NAME_ACCESS, sign);
    }
}
