package io.paradaux.chestshop.listeners;

import io.paradaux.chestshop.services.AdminBypass;
import io.paradaux.chestshop.services.ShopBlockService;
import com.google.inject.Inject;
import io.paradaux.chestshop.utils.*;
import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.model.config.ChestShopConfiguration;
import io.paradaux.chestshop.model.Account;
import io.paradaux.chestshop.model.PreTransactionContext;
import io.paradaux.chestshop.model.TransactionContext;
import io.paradaux.chestshop.utils.Permissions;
import io.paradaux.chestshop.services.Security;
import io.paradaux.chestshop.services.AccountService;
import io.paradaux.chestshop.services.InfoService;
import io.paradaux.chestshop.services.ItemService;
import io.paradaux.chestshop.services.TransactionService;
import io.paradaux.chestshop.services.ChestShopSign;
import io.paradaux.hibernia.framework.i18n.Message;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.logging.Level;

import static io.paradaux.chestshop.utils.ImplementationAdapter.getState;
import static io.paradaux.chestshop.utils.BlockUtil.isSign;
import static io.paradaux.chestshop.utils.Permissions.OTHER_NAME_CREATE;
import static io.paradaux.chestshop.services.ChestShopSign.*;
import static org.bukkit.event.block.Action.LEFT_CLICK_BLOCK;
import static org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK;

/**
 * @author Acrobot
 */
public class PlayerInteract implements Listener {

    /**
     * Per-player timestamp of the last shop-trade click, used to throttle the
     * expensive trade resolution below (ADT-44). Main-thread only, so a plain
     * {@link WeakHashMap} is safe; weak keys let logged-out players' entries be
     * collected without an explicit quit handler.
     */
    private static final Map<Player, Long> LAST_TRADE_CLICK = new WeakHashMap<>();

    private final TransactionService transactions;
    private final InfoService info;
    private final AccountService accounts;
    private final ItemService items;
    private final Message message;
    private final Security security;
    private final ChestShopConfiguration config;
    private final ChestShopSign chestShopSign;
    private final ShopBlockService shopBlockService;

    private final AdminBypass adminBypass;

    @Inject
    public PlayerInteract(TransactionService transactions, InfoService info, AccountService accounts,
                          ItemService items, Message message, Security security,
                          ChestShopConfiguration config, ChestShopSign chestShopSign, ShopBlockService shopBlockService, AdminBypass adminBypass) {
        this.adminBypass = adminBypass;
        this.transactions = transactions;
        this.info = info;
        this.accounts = accounts;
        this.items = items;
        this.message = message;
        this.security = security;
        this.config = config;
        this.chestShopSign = chestShopSign;
        this.shopBlockService = shopBlockService;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null)
            return;

        Action action = event.getAction();
        Player player = event.getPlayer();

        if (config.isUseBuiltInProtection() && shopBlockService.couldBeShopContainer(block)) {
            Sign sign = shopBlockService.getConnectedSign(block);
            if (sign != null) {

                if (!security.canView(player, block, config.isTurnOffDefaultProtectionWhenProtectedExternally())) {
                    if (adminBypass.has(player, Permissions.SHOPINFO)) {
                        info.showShopInfo(player, sign);
                        event.setCancelled(true);
                    } else if (!config.isTurnOffDefaultProtectionWhenProtectedExternally()) {
                        message.send(player, "chestshop.ACCESS_DENIED", "prefix", "");
                        event.setCancelled(true);
                    }
                }

                return;
            }
        }

        if (!isSign(block))
            return;

        Sign sign = (Sign) getState(block, false);
        if (!chestShopSign.isValid(sign)) {
            return;
        }

        if (config.isAllowAutoItemFill() && ChatColor.stripColor(ChestShopSign.getItem(sign)).equals(AUTOFILL_CODE)) {
            if (accounts.hasPermission(player, OTHER_NAME_CREATE, sign)) {
                ItemStack item = player.getInventory().getItemInMainHand();
                if (!MaterialUtil.isEmpty(item)) {
                    event.setCancelled(true);
                    String itemCode;
                    try {
                        itemCode = items.getSignName(item);
                    } catch (IllegalArgumentException e) {
                        player.sendMessage(ChatColor.RED + "Error while generating shop sign item name. Please contact an admin or take a look at the console/log!");
                        io.paradaux.chestshop.ChestShop.getPlugin().getLogger().log(Level.SEVERE, "Error while generating shop sign item name", e);
                        return;
                    }
                    String[] lines = sign.getLines();
                    lines[ITEM_LINE] = itemCode;

                    SignChangeEvent changeEvent = new SignChangeEvent(block, player, lines);
                    io.paradaux.chestshop.ChestShop.callEvent(changeEvent);
                    if (!changeEvent.isCancelled()) {
                        for (byte i = 0; i < changeEvent.getLines().length; ++i) {
                            String line = changeEvent.getLine(i);
                            sign.setLine(i, line != null ? line : "");
                        }
                        sign.update();
                    }
                } else {
                    message.send(player, "chestshop.NO_ITEM_IN_HAND");
                }
            } else {
                message.send(player, "chestshop.ACCESS_DENIED");
            }
            return;
        }

        boolean notAllowedToTrade = accounts.isOwner(player, sign)
                || (config.isIgnoreAccessPerms() && accounts.canAccess(player, sign));
        if (notAllowedToTrade && player.getInventory().getItemInMainHand().getType().name().contains("SIGN") && action == RIGHT_CLICK_BLOCK) {
            // Allow editing of sign (if supported)
            return;
        } else if ((player.getInventory().getItemInMainHand().getType().name().endsWith("DYE")
                || player.getInventory().getItemInMainHand().getType().name().endsWith("INK_SAC"))
                && action == RIGHT_CLICK_BLOCK) {
            if (notAllowedToTrade && config.isSignDying()) {
                return;
            } else {
                event.setCancelled(true);
            }
        }

        if (notAllowedToTrade && accounts.canAccess(player, sign) && !chestShopSign.isAdminShop(sign)) {
            if (config.isAllowSignChestOpen() && !(config.isIgnoreCreativeMode() && player.getGameMode() == GameMode.CREATIVE)) {
                if (player.isSneaking() || player.isInsideVehicle()
                        || (config.isAllowLeftClickDestroying() && action == LEFT_CLICK_BLOCK)) {
                    return;
                }
                event.setCancelled(true);
                showChestGUI(player, block, sign);
                return;
            }
            // don't allow owners or people with access to buy/sell at this shop
            message.send(player, "chestshop.TRADE_DENIED_ACCESS_PERMS");
            if (action == RIGHT_CLICK_BLOCK) {
                // don't allow editing
                event.setCancelled(true);
            }
            return;
        }

        if (action == RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
        } else if (action == LEFT_CLICK_BLOCK && !config.isTurnOffSignProtection() && !accounts.canAccess(player, sign)) {
            event.setCancelled(true);
        }

        if (config.isCheckAccessForShopUse() && !security.canAccess(player, block, true)) {
            message.send(player, "chestshop.TRADE_DENIED");
            return;
        }

        // Throttle per player BEFORE the expensive trade resolution. The prep
        // below dispatches AccountQuery/AccountCheck/ItemParse events and scans
        // inventories; an auto-clicker would otherwise force that work (and a
        // DB/economy round-trip) every tick. The old SpamClickProtector only
        // cancelled the already-built PreTransactionContext, i.e. after the cost
        // was already paid (ADT-44).
        long now = System.currentTimeMillis();
        Long lastClick = LAST_TRADE_CLICK.get(player);
        if (lastClick != null && (now - lastClick) < config.getShopInteractionInterval()) {
            return;
        }
        LAST_TRADE_CLICK.put(player, now);

        PreTransactionContext pEvent = transactions.prepare(sign, player, action);
        if (pEvent == null)
            return;

        transactions.validate(pEvent);
        if (pEvent.isCancelled())
            return;

        TransactionContext tEvent = new TransactionContext(pEvent, sign);
        transactions.process(tEvent);
    }

    private void showChestGUI(Player player, Block signBlock, Sign sign) {
        Container container = shopBlockService.findConnectedContainer(sign);

        if (container == null) {
            message.send(player, "chestshop.NO_CHEST_DETECTED");
            return;
        }

        if (!security.canAccess(player, signBlock)) {
            return;
        }
        
        if (!security.canAccess(player, container.getBlock())) {
            return;
        }

        BlockUtil.openBlockGUI(container, player);
    }
}
