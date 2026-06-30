package io.paradaux.chestshop.listeners.player;

import com.google.inject.Inject;
import io.paradaux.chestshop.utils.*;
import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.configuration.Properties;
import io.paradaux.chestshop.utils.AdminInventory;
import io.paradaux.chestshop.model.Account;
import io.paradaux.chestshop.context.PreTransactionContext;
import io.paradaux.chestshop.context.TransactionContext;
import io.paradaux.chestshop.permission.Permissions;
import io.paradaux.chestshop.services.Security;
import io.paradaux.chestshop.services.AccountService;
import io.paradaux.chestshop.services.EconomyService;
import io.paradaux.chestshop.services.InfoService;
import io.paradaux.chestshop.services.ItemService;
import io.paradaux.chestshop.services.TransactionService;
import io.paradaux.chestshop.signs.ChestShopSign;
import io.paradaux.chestshop.utils.ShopBlockUtil;
import io.paradaux.hibernia.framework.i18n.Message;
import org.bukkit.Bukkit;
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

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.logging.Level;

import static io.paradaux.chestshop.utils.ImplementationAdapter.getState;
import static io.paradaux.chestshop.utils.BlockUtil.isSign;
import static io.paradaux.chestshop.context.TransactionContext.TransactionType;
import static io.paradaux.chestshop.context.TransactionContext.TransactionType.BUY;
import static io.paradaux.chestshop.context.TransactionContext.TransactionType.SELL;
import static io.paradaux.chestshop.permission.Permissions.OTHER_NAME_CREATE;
import static io.paradaux.chestshop.signs.ChestShopSign.*;
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
    private final EconomyService economy;
    private final ItemService items;
    private final Message message;
    private final Security security;

    @Inject
    public PlayerInteract(TransactionService transactions, InfoService info, AccountService accounts,
                          EconomyService economy, ItemService items, Message message, Security security) {
        this.transactions = transactions;
        this.info = info;
        this.accounts = accounts;
        this.economy = economy;
        this.items = items;
        this.message = message;
        this.security = security;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null)
            return;

        Action action = event.getAction();
        Player player = event.getPlayer();

        if (Properties.USE_BUILT_IN_PROTECTION && ShopBlockUtil.couldBeShopContainer(block)) {
            Sign sign = ShopBlockUtil.getConnectedSign(block);
            if (sign != null) {

                if (!security.canView(player, block, Properties.TURN_OFF_DEFAULT_PROTECTION_WHEN_PROTECTED_EXTERNALLY)) {
                    if (Permissions.has(player, Permissions.SHOPINFO)) {
                        info.showShopInfo(player, sign);
                        event.setCancelled(true);
                    } else if (!Properties.TURN_OFF_DEFAULT_PROTECTION_WHEN_PROTECTED_EXTERNALLY) {
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
        if (!ChestShopSign.isValid(sign)) {
            return;
        }

        if (Properties.ALLOW_AUTO_ITEM_FILL && ChatColor.stripColor(ChestShopSign.getItem(sign)).equals(AUTOFILL_CODE)) {
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
                || (Properties.IGNORE_ACCESS_PERMS && accounts.canAccess(player, sign));
        if (notAllowedToTrade && player.getInventory().getItemInMainHand().getType().name().contains("SIGN") && action == RIGHT_CLICK_BLOCK) {
            // Allow editing of sign (if supported)
            return;
        } else if ((player.getInventory().getItemInMainHand().getType().name().endsWith("DYE")
                || player.getInventory().getItemInMainHand().getType().name().endsWith("INK_SAC"))
                && action == RIGHT_CLICK_BLOCK) {
            if (notAllowedToTrade && Properties.SIGN_DYING) {
                return;
            } else {
                event.setCancelled(true);
            }
        }

        if (notAllowedToTrade && accounts.canAccess(player, sign) && !ChestShopSign.isAdminShop(sign)) {
            if (Properties.ALLOW_SIGN_CHEST_OPEN && !(Properties.IGNORE_CREATIVE_MODE && player.getGameMode() == GameMode.CREATIVE)) {
                if (player.isSneaking() || player.isInsideVehicle()
                        || (Properties.ALLOW_LEFT_CLICK_DESTROYING && action == LEFT_CLICK_BLOCK)) {
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
        } else if (action == LEFT_CLICK_BLOCK && !Properties.TURN_OFF_SIGN_PROTECTION && !accounts.canAccess(player, sign)) {
            event.setCancelled(true);
        }

        if (Properties.CHECK_ACCESS_FOR_SHOP_USE && !security.canAccess(player, block, true)) {
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
        if (lastClick != null && (now - lastClick) < Properties.SHOP_INTERACTION_INTERVAL) {
            return;
        }
        LAST_TRADE_CLICK.put(player, now);

        PreTransactionContext pEvent = preparePreTransactionContext(sign, player, action);
        if (pEvent == null)
            return;

        transactions.validate(pEvent);
        if (pEvent.isCancelled())
            return;

        TransactionContext tEvent = new TransactionContext(pEvent, sign);
        transactions.process(tEvent);
    }

    private PreTransactionContext preparePreTransactionContext(Sign sign, Player player, Action action) {
        String name = ChestShopSign.getOwner(sign);
        String prices = ChestShopSign.getPrice(sign);
        String material = ChestShopSign.getItem(sign);

        Account account = accounts.resolveAccount(name);
        if (account == null) {
            message.send(player, "chestshop.PLAYER_NOT_FOUND");
            return null;
        }

        boolean adminShop = ChestShopSign.isAdminShop(sign);

        // check if player exists in economy
        if (!adminShop && !economy.hasAccount(account.getUuid())) {
            message.send(player, "chestshop.NO_ECONOMY_ACCOUNT");
            return null;
        }

        Action buy = Properties.REVERSE_BUTTONS ? LEFT_CLICK_BLOCK : RIGHT_CLICK_BLOCK;
        BigDecimal price = (action == buy ? PriceUtil.getExactBuyPrice(prices) : PriceUtil.getExactSellPrice(prices));

        Container shopBlock = ShopBlockUtil.findConnectedContainer(sign);
        Inventory ownerInventory = shopBlock != null ? shopBlock.getInventory() : null;

        ItemStack item = items.parse(material);
        if (item == null) {
            message.send(player, "chestshop.INVALID_SHOP_DETECTED");
            return null;
        }

        int amount = -1;
        try {
            amount = ChestShopSign.getQuantity(sign);
        } catch (NumberFormatException ignored) {} // There is no quantity number on the sign

        if (amount < 1 || amount > Properties.MAX_SHOP_AMOUNT) {
            message.send(player, "chestshop.INVALID_SHOP_PRICE");
            return null;
        }

        BigDecimal pricePerItem = price.divide(BigDecimal.valueOf(amount), MathContext.DECIMAL128);
        if (Properties.SHIFT_SELLS_IN_STACKS && player.isSneaking() && !price.equals(PriceUtil.NO_PRICE) && isAllowedForShift(action == buy)) {
            int newAmount = adminShop ? InventoryUtil.getMaxStackSize(item) : getStackAmount(item, ownerInventory, player, action);
            if (newAmount > 0) {
                price = pricePerItem.multiply(BigDecimal.valueOf(newAmount)).setScale(Properties.PRICE_PRECISION, RoundingMode.HALF_UP);
                amount = newAmount;
            }
        } else if (Properties.SHIFT_SELLS_EVERYTHING && player.isSneaking() && !price.equals(PriceUtil.NO_PRICE) && isAllowedForShift(action == buy)) {
            if (action != buy) {
                int newAmount = InventoryUtil.getAmount(item, player.getInventory());
                if (newAmount > 0) {
                    price = pricePerItem.multiply(BigDecimal.valueOf(newAmount)).setScale(Properties.PRICE_PRECISION, RoundingMode.HALF_UP);
                    amount = newAmount;
                }
            } else if (!adminShop && ownerInventory != null) {
                int newAmount = InventoryUtil.getAmount(item, ownerInventory);
                if (newAmount > 0) {
                    price = pricePerItem.multiply(BigDecimal.valueOf(newAmount)).setScale(Properties.PRICE_PRECISION, RoundingMode.HALF_UP);
                    amount = newAmount;
                }
            }
        }

        item.setAmount(amount);

        ItemStack[] items = InventoryUtil.getItemsStacked(item);

        // Create virtual admin inventory if
        // - it's an admin shop
        // - there is no container for the shop sign
        // - the config doesn't force unlimited admin shop stock
        if (adminShop && (ownerInventory == null || Properties.FORCE_UNLIMITED_ADMIN_SHOP)) {
            ownerInventory = new AdminInventory(action == buy ? Arrays.stream(items).map(ItemStack::clone).toArray(ItemStack[]::new) : new ItemStack[0]);
        }

        TransactionType transactionType = (action == buy ? BUY : SELL);
        return new PreTransactionContext(ownerInventory, player.getInventory(), items, price, player, account, sign, transactionType);
    }

    private static boolean isAllowedForShift(boolean buyTransaction) {
        String allowed = Properties.SHIFT_ALLOWS;

        if (allowed.equalsIgnoreCase("ALL")) {
            return true;
        }

        return allowed.equalsIgnoreCase(buyTransaction ? "BUY" : "SELL");
    }

    private static int getStackAmount(ItemStack item, Inventory inventory, Player player, Action action) {
        Action buy = Properties.REVERSE_BUTTONS ? LEFT_CLICK_BLOCK : RIGHT_CLICK_BLOCK;
        Inventory checkedInventory = (action == buy ? inventory : player.getInventory());

        if (checkedInventory.containsAtLeast(item, InventoryUtil.getMaxStackSize(item))) {
            return InventoryUtil.getMaxStackSize(item);
        } else {
            return InventoryUtil.getAmount(item, checkedInventory);
        }
    }

    /**
     * @deprecated Use {@link ChestShopSign#hasPermission(Player, Permission, Sign)} with {@link Permission#OTHER_NAME_ACCESS}
     */
    @Deprecated
    public static boolean canOpenOtherShops(Player player) {
        return Permissions.has(player, Permissions.OTHER_NAME_ACCESS + ".*");
    }

    private void showChestGUI(Player player, Block signBlock, Sign sign) {
        Container container = ShopBlockUtil.findConnectedContainer(sign);

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
