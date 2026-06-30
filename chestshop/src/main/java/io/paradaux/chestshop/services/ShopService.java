package io.paradaux.chestshop.services;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.permission.Permissions;
import io.paradaux.chestshop.configuration.Properties;
import io.paradaux.hibernia.framework.i18n.Message;
import io.paradaux.chestshop.model.Account;
import io.paradaux.chestshop.context.PreShopCreationContext;
import io.paradaux.chestshop.context.ShopCreatedContext;
import io.paradaux.chestshop.context.ShopDestroyedContext;
import io.paradaux.chestshop.services.Security;
import io.paradaux.chestshop.context.PreShopCreationContext.CreationOutcome;
import io.paradaux.chestshop.listeners.modules.StockCounterModule;
import io.paradaux.chestshop.market.MarketListener;
import io.paradaux.chestshop.signs.ChestShopSign;
import io.paradaux.chestshop.utils.LocationUtil;
import io.paradaux.chestshop.utils.MaterialUtil;
import io.paradaux.chestshop.utils.PriceUtil;
import io.paradaux.chestshop.utils.StringUtil;
import io.paradaux.chestshop.utils.ShopBlockUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.logging.Level;

import static io.paradaux.chestshop.permission.Permissions.NOFEE;
import static io.paradaux.chestshop.signs.ChestShopSign.AUTOFILL_CODE;

/**
 * Owns the whole shop lifecycle: creation validation ({@link #create}), the post-creation
 * and post-removal reactions ({@link #onCreated}/{@link #onDestroyed}), and the
 * creation-fee / removal-refund money (with the mirrored server-economy movement).
 * This replaces ChestShop's old event-bus design — a {@code PreShopCreationContext} fanned
 * out to a dozen priority-ordered validator classes coordinating through a mutable
 * outcome/signLines bag — with one service whose validation steps are ordinary ordered
 * private methods (PAR-282). The genuine cross-cutting hooks (market-DB sync, stock
 * counter) and {@code Security}/{@code ProtectionService} integration stay.
 *
 * <p>Shops have no ChestShop-owned persistence — they are sign + chest world state — so
 * there is no repository here, only this service.
 */
@Singleton
public class ShopService {

    private final AccountService accounts;
    private final EconomyService economy;
    private final ItemService items;
    private final ProtectionService protection;
    private final StockCounterModule stockCounter;
    private final Message message;
    private final Security security;
    private final MarketListener market;

    @Inject
    public ShopService(AccountService accounts, EconomyService economy, ItemService items, ProtectionService protection, StockCounterModule stockCounter, Message message, Security security, MarketListener market) {
        this.accounts = accounts;
        this.economy = economy;
        this.items = items;
        this.protection = protection;
        this.stockCounter = stockCounter;
        this.message = message;
        this.security = security;
        this.market = market;
    }

    /**
     * Run a shop-sign creation through the validation steps and return the result context
     * for the caller ({@code SignCreate}) to act on. The steps run in the exact order the
     * former priority-ordered validators fired; like the originals (almost all
     * {@code ignoreCancelled=false}) each runs unconditionally and may overwrite the
     * outcome — so the reported failure is the last problem found. The two former
     * {@code ignoreCancelled=true} steps (the creation-fee charge and the second name
     * pass) run only while the creation is still un-cancelled.
     */
    public PreShopCreationContext create(Player player, Sign sign, String[] signLines) {
        PreShopCreationContext ctx = new PreShopCreationContext(player, sign, signLines);

        // LOWEST
        checkItem(ctx);
        checkPrice(ctx);
        checkQuantity(ctx);
        // LOW
        checkChest(ctx);
        resolveName(ctx);
        // NORMAL
        checkCreationFunds(ctx);
        rejectFreeShop(ctx);
        checkTerrain(ctx);
        // HIGH
        if (Properties.BLOCK_SHOPS_WITH_SELL_PRICE_HIGHER_THAN_BUY_PRICE) {
            checkPriceRatio(ctx);
        }
        checkCreationPermission(ctx);
        if (!ctx.isCancelled()) { // CreationFeeGetter ran @HIGH with ignoreCancelled=true
            chargeCreationFeeStep(ctx);
        }
        // StockCounterModule ran @HIGH with the default ignoreCancelled=false, so it
        // fires regardless: it normalises the quantity line and seeds the stock counter.
        stockCounter.onPreShopCreation(ctx);
        // HIGHEST — the name second pass (ignoreCancelled=true)
        if (!ctx.isCancelled()) {
            resolveName(ctx);
        }
        // MONITOR — tell the player why it failed
        sendCreationError(ctx);

        return ctx;
    }

    // ---- creation validation steps (were the preshopcreation listeners) ---------

    /** Resolve/normalise the item line; autofill from the chest, or reject an invalid item. */
    private void checkItem(PreShopCreationContext ctx) {
        String itemCode = ChestShopSign.getItem(ctx.getSignLines());
        ItemStack item = items.parse(itemCode);

        if (item == null) {
            if (Properties.ALLOW_AUTO_ITEM_FILL && itemCode.equals(AUTOFILL_CODE)) {
                Container container = ShopBlockUtil.findConnectedContainer(ctx.getSign());
                if (container != null) {
                    for (ItemStack stack : container.getInventory().getContents()) {
                        if (!MaterialUtil.isEmpty(stack)) {
                            item = stack;
                            break;
                        }
                    }
                }
                if (item == null) {
                    ctx.setSignLine(ChestShopSign.ITEM_LINE, ChatColor.BOLD + ChestShopSign.AUTOFILL_CODE);
                    ctx.setOutcome(CreationOutcome.ITEM_AUTOFILL);
                    return;
                }
            } else {
                ctx.setOutcome(CreationOutcome.INVALID_ITEM);
                return;
            }
        }

        itemCode = items.getSignName(item);
        if (StringUtil.getMinecraftStringWidth(itemCode) > MaterialUtil.MAXIMUM_SIGN_WIDTH) {
            ctx.setOutcome(CreationOutcome.INVALID_ITEM);
            return;
        }
        ctx.setSignLine(ChestShopSign.ITEM_LINE, itemCode);
    }

    /** Normalise the price line (B/S prefixes, precision, multipliers) and reject if invalid.
     *  Package-private so the price-parser unit test can exercise it directly. */
    void checkPrice(PreShopCreationContext ctx) {
        String line = ChestShopSign.getPrice(ctx.getSignLines()).toUpperCase(Locale.ROOT);
        if (Properties.PRICE_PRECISION <= 0) {
            line = line.replaceAll("\\.\\d*", ""); // remove too many decimal places
        } else {
            line = line.replaceAll("(\\.\\d{0," + Properties.PRICE_PRECISION + "})\\d*", "$1");
        }
        line = line.replaceAll("(\\.\\d*[1-9])0+", "$1"); // trailing zeroes
        line = line.replaceAll("(\\d)\\.0+(\\D|$)", "$1$2"); // point + zeroes when only trailing zeros

        String[] parts = line.split(":");
        if (parts.length > 1 && (isInvalidPricePart(parts[0]) ^ isInvalidPricePart(parts[1]))) {
            line = line.replace(':', ' ');
            parts = new String[]{line};
        }
        if (parts[0].split(" ").length > 2
                || line.indexOf('B') != line.lastIndexOf('B')
                || line.indexOf('S') != line.lastIndexOf('S')) {
            ctx.setOutcome(CreationOutcome.INVALID_PRICE);
            return;
        }
        if (PriceUtil.isPrice(parts[0])) {
            line = "B " + line;
        }
        if (parts.length > 1 && PriceUtil.isPrice(parts[1])) {
            line += " S";
        }
        if (line.length() > 15) {
            line = line.replace(" ", "");
        }
        if (line.length() > 15) {
            ctx.setOutcome(CreationOutcome.INVALID_PRICE);
            return;
        }
        for (String part : parts) {
            if (!PriceUtil.hasSingleMultiplier(part)) {
                ctx.setOutcome(CreationOutcome.INVALID_PRICE);
                return;
            }
        }
        ctx.setSignLine(ChestShopSign.PRICE_LINE, line);
        if (!PriceUtil.hasBuyPrice(line) && !PriceUtil.hasSellPrice(line)) {
            ctx.setOutcome(CreationOutcome.INVALID_PRICE);
        }
    }

    private static boolean isInvalidPricePart(String part) {
        for (char character : new char[]{'B', 'S'}) {
            if (part.contains(Character.toString(character))) {
                return !PriceUtil.hasPrice(part, character);
            }
        }
        return false;
    }

    /** Reject a missing / out-of-range quantity line. */
    private void checkQuantity(PreShopCreationContext ctx) {
        int amount = -1;
        try {
            amount = ChestShopSign.getQuantity(ctx.getSignLines());
        } catch (NumberFormatException ignored) {} // not a quantity on the line
        if (amount < 1 || amount > Properties.MAX_SHOP_AMOUNT) {
            ctx.setOutcome(CreationOutcome.INVALID_QUANTITY);
        }
    }

    /** Require a backing chest (non-admin) the creator may access. */
    private void checkChest(PreShopCreationContext ctx) {
        String nameLine = ChestShopSign.getOwner(ctx.getSignLines());
        Container connectedContainer = ShopBlockUtil.findConnectedContainer(ctx.getSign().getBlock());

        if (connectedContainer == null) {
            if (!ChestShopSign.isAdminShop(nameLine)) {
                ctx.setOutcome(CreationOutcome.NO_CHEST);
            }
            return;
        }
        Player player = ctx.getPlayer();
        if (Permissions.has(player, Permissions.ADMIN)) {
            return;
        }
        if (!security.canAccess(player, connectedContainer.getBlock())) {
            ctx.setOutcome(CreationOutcome.NO_PERMISSION_FOR_CHEST);
        }
    }

    /** Charge / require the configured creation fee. */
    private void checkCreationFunds(PreShopCreationContext ctx) {
        BigDecimal shopCreationPrice = Properties.SHOP_CREATION_PRICE;
        if (shopCreationPrice.compareTo(BigDecimal.ZERO) == 0
                || ChestShopSign.isAdminShop(ctx.getSignLines())
                || Permissions.has(ctx.getPlayer(), NOFEE)) {
            return;
        }
        if (!economy.hasFunds(ctx.getPlayer().getUniqueId(), shopCreationPrice)) {
            ctx.setOutcome(CreationOutcome.NOT_ENOUGH_MONEY);
        }
    }

    /** DemocracyCraft: reject free (price-0) shops unless {@link Properties#ALLOW_FREE_SHOPS} (PAR-88).
     *  Package-private so the free-shop unit test can exercise it directly. */
    void rejectFreeShop(PreShopCreationContext ctx) {
        if (Properties.ALLOW_FREE_SHOPS) {
            return;
        }
        String price = ChestShopSign.getPrice(ctx.getSignLines());
        if (isFreePrice(PriceUtil.getExactBuyPrice(price)) || isFreePrice(PriceUtil.getExactSellPrice(price))) {
            ctx.setOutcome(CreationOutcome.INVALID_PRICE);
        }
    }

    private static boolean isFreePrice(BigDecimal price) {
        return price.compareTo(PriceUtil.FREE) == 0;
    }

    /** Require terrain build permission for the sign and its chest. */
    private void checkTerrain(PreShopCreationContext ctx) {
        Player player = ctx.getPlayer();
        if (!security.canPlaceSign(player, ctx.getSign())) {
            ctx.setOutcome(CreationOutcome.NO_PERMISSION_FOR_TERRAIN);
            return;
        }
        Container connectedContainer = ShopBlockUtil.findConnectedContainer(ctx.getSign().getBlock());
        Location containerLocation = connectedContainer != null ? connectedContainer.getLocation() : null;
        if (!protection.canBuild(player, containerLocation, ctx.getSign().getLocation())) {
            ctx.setOutcome(CreationOutcome.NO_PERMISSION_FOR_TERRAIN);
        }
    }

    /** Optionally reject a shop whose sell price exceeds its buy price. */
    private void checkPriceRatio(PreShopCreationContext ctx) {
        String priceLine = ChestShopSign.getPrice(ctx.getSignLines());
        if (PriceUtil.hasBuyPrice(priceLine) && PriceUtil.hasSellPrice(priceLine)
                && PriceUtil.getExactSellPrice(priceLine).compareTo(PriceUtil.getExactBuyPrice(priceLine)) > 0) {
            ctx.setOutcome(CreationOutcome.SELL_PRICE_HIGHER_THAN_BUY_PRICE);
        }
    }

    /** Per-name / per-item shop-creation permission check. */
    private void checkCreationPermission(PreShopCreationContext ctx) {
        Player player = ctx.getPlayer();

        if (ctx.getOwnerAccount() != null
                && !accounts.canUseName(player, Permissions.OTHER_NAME_CREATE, ctx.getOwnerAccount().getShortName())) {
            ctx.setSignLine(ChestShopSign.NAME_LINE, "");
            ctx.setOutcome(CreationOutcome.NO_PERMISSION);
            return;
        }

        String priceLine = ChestShopSign.getPrice(ctx.getSignLines());
        String itemLine = ChestShopSign.getItem(ctx.getSignLines());
        ItemStack item = items.parse(itemLine);

        if (item == null) {
            if (PriceUtil.hasBuyPrice(priceLine) && !Permissions.has(player, Permissions.SHOP_CREATION_BUY)
                    || PriceUtil.hasSellPrice(priceLine) && !Permissions.has(player, Permissions.SHOP_CREATION_SELL)) {
                ctx.setOutcome(CreationOutcome.NO_PERMISSION);
            }
            return;
        }

        String matID = item.getType().toString().toLowerCase(Locale.ROOT);
        String[] parts = itemLine.split("#", 2);
        if (parts.length == 2 && Permissions.hasPermissionSetFalse(player, Permissions.SHOP_CREATION_ID + matID + "#" + parts[1])) {
            ctx.setOutcome(CreationOutcome.NO_PERMISSION);
            return;
        }

        if (PriceUtil.hasBuyPrice(priceLine)) {
            if (!canCreateForItem(player, Permissions.SHOP_CREATION_BUY_ID + matID, matID, Permissions.SHOP_CREATION_BUY)) {
                ctx.setOutcome(CreationOutcome.NO_PERMISSION);
            }
        } else if (PriceUtil.hasSellPrice(priceLine)) {
            if (!canCreateForItem(player, Permissions.SHOP_CREATION_SELL_ID + matID, matID, Permissions.SHOP_CREATION_SELL)) {
                ctx.setOutcome(CreationOutcome.NO_PERMISSION);
            }
        }
    }

    /** Shared buy/sell creation-permission test for a specific material. */
    private static boolean canCreateForItem(Player player, String perItemSideNode, String matID, String sideNode) {
        return Permissions.has(player, perItemSideNode)
                || Permissions.has(player, Permissions.SHOP_CREATION)
                || Permissions.has(player, Permissions.SHOP_CREATION_ID + matID) && Permissions.has(player, sideNode);
    }

    /** Charge the creation fee (the former {@code ignoreCancelled=true} CreationFeeGetter step). */
    private void chargeCreationFeeStep(PreShopCreationContext ctx) {
        if (!chargeCreationFee(ctx.getPlayer(), ctx.getSignLines())) {
            ctx.setOutcome(CreationOutcome.NOT_ENOUGH_MONEY);
            ctx.setSignLines(new String[4]);
        }
    }

    /** Resolve the owner account from the name line (player or B:&lt;id&gt; business). */
    private void resolveName(PreShopCreationContext ctx) {
        String name = ChestShopSign.getOwner(ctx.getSignLines());
        Player player = ctx.getPlayer();

        if (ChestShopSign.isBusinessAccount(name)) {
            resolveBusinessName(ctx, player, name);
            return;
        }

        Account account = ctx.getOwnerAccount();
        if (account == null || !account.getShortName().equalsIgnoreCase(name)) {
            account = null;
            try {
                if (name.isEmpty() || !accounts.canUseName(player, Permissions.OTHER_NAME_CREATE, name)) {
                    account = accounts.getOrCreateAccount(player);
                } else {
                    account = accounts.resolveAccount(name);
                    if (account == null) {
                        Player otherPlayer = ChestShop.getBukkitServer().getPlayer(name);
                        try {
                            account = otherPlayer != null
                                    ? accounts.getOrCreateAccount(otherPlayer)
                                    : accounts.getOrCreateAccount(ChestShop.getBukkitServer().getOfflinePlayer(name));
                        } catch (IllegalArgumentException e) {
                            ctx.getPlayer().sendMessage(e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                ChestShop.getBukkitLogger().log(Level.SEVERE, "Error while trying to check account for name " + name + " with player " + player.getName(), e);
            }
        }
        ctx.setOwnerAccount(account);
        if (account != null) {
            ctx.setSignLine(ChestShopSign.NAME_LINE, account.getShortName());
        } else {
            ctx.setSignLine(ChestShopSign.NAME_LINE, "");
            ctx.setOutcome(CreationOutcome.UNKNOWN_PLAYER);
        }
    }

    /**
     * Validate and resolve a business-account ({@code B:<base36>}) name: Treasury must be
     * present, the account must exist, and (unless the creator is a ChestShop admin) the
     * creator must hold the firm's CHESTSHOP permission.
     */
    private void resolveBusinessName(PreShopCreationContext ctx, Player player, String name) {
        Account existing = ctx.getOwnerAccount();
        if (existing != null && existing.getShortName().equalsIgnoreCase(name)) {
            return; // already resolved on a previous pass
        }

        if (Bukkit.getPluginManager().getPlugin("Treasury") == null) {
            message.send(player, "chestshop.TREASURY_REQUIRED");
            failName(ctx);
            return;
        }
        Account account = accounts.resolveAccount(name);
        if (account == null) {
            message.send(player, "chestshop.BUSINESS_ACCOUNT_NOT_FOUND");
            failName(ctx);
            return;
        }
        if (!Permissions.has(player, Permissions.ADMIN) && !accounts.canAccess(player, account)) {
            message.send(player, "chestshop.BUSINESS_NO_CHESTSHOP_PERMISSION");
            failName(ctx);
            return;
        }
        ctx.setOwnerAccount(account);
        int accountId = ChestShopSign.getBusinessAccountId(account.getShortName());
        ctx.setSignLine(ChestShopSign.NAME_LINE, ChestShopSign.businessAccountSignName(accountId));
    }

    private void failName(PreShopCreationContext ctx) {
        ctx.setSignLine(ChestShopSign.NAME_LINE, "");
        ctx.setOutcome(CreationOutcome.UNKNOWN_PLAYER);
    }

    /** Tell the creator why the shop could not be made (was the MONITOR ErrorMessageSender). */
    private void sendCreationError(PreShopCreationContext ctx) {
        if (!ctx.isCancelled()) {
            return;
        }
        String messageKey = switch (ctx.getOutcome()) {
            case UNKNOWN_PLAYER -> "chestshop.PLAYER_NOT_FOUND";
            case INVALID_ITEM -> "chestshop.INCORRECT_ITEM_ID";
            case INVALID_PRICE -> "chestshop.INVALID_SHOP_PRICE";
            case INVALID_QUANTITY -> "chestshop.INVALID_SHOP_QUANTITY";
            case SELL_PRICE_HIGHER_THAN_BUY_PRICE -> "chestshop.SELL_PRICE_HIGHER_THAN_BUY_PRICE";
            case NO_CHEST -> "chestshop.NO_CHEST_DETECTED";
            case NO_PERMISSION -> "chestshop.NO_PERMISSION";
            case NO_PERMISSION_FOR_TERRAIN -> "chestshop.CANNOT_CREATE_SHOP_HERE";
            case NO_PERMISSION_FOR_CHEST -> "chestshop.CANNOT_ACCESS_THE_CHEST";
            case NOT_ENOUGH_MONEY -> "chestshop.NOT_ENOUGH_MONEY";
            case ITEM_AUTOFILL -> "chestshop.CLICK_TO_AUTOFILL_ITEM";
            default -> null;
        };
        if (messageKey != null) {
            message.send(ctx.getPlayer(), messageKey);
        }
    }

    /**
     * Run the post-creation reactions for a freshly-created shop, in the exact priority
     * + registration order the former {@link ShopCreatedContext} listeners fired in
     * (replacing the Bukkit event dispatch): NORMAL {@code SignSticker} sticks the sign
     * to its chest, then MONITOR {@code MessageSender} notifies the creator,
     * {@code ShopCreationLogger} logs it, and {@code MarketListener} upserts the shop in
     * the market registry.
     */
    public void onCreated(ShopCreatedContext event) {
        stickSignToChest(event);   // was @NORMAL SignSticker
        sendCreatedMessage(event); // was @MONITOR MessageSender
        logCreation(event);        // was @MONITOR ShopCreationLogger
        market.onShopCreated(event); // genuine market-DB sync — stays
    }

    /**
     * Run the post-removal reactions for a removed shop, in the former {@link ShopDestroyedContext}
     * listener order (all MONITOR): issue the removal refund, log the removal, then mark
     * the shop inactive in the market registry.
     */
    public void onDestroyed(ShopDestroyedContext event) {
        refundOnRemoval(event.getDestroyer(), event.getSign());
        logRemoval(event);                     // was @MONITOR ShopRemovalLogger
        market.onShopDestroyed(event); // genuine market-DB sync — stays
    }

    // ---- post-creation reactions (were the postshopcreation listeners) ----------

    private static final String CREATION_LOG = "%1$s created %2$s - %3$s - %4$s - at %5$s";
    private static final String REMOVAL_LOG = "%1$s was removed by %2$s - %3$s - %4$s - at %5$s";

    /** Stick a freshly-created shop sign onto its chest (config-gated, never for admin shops). */
    private void stickSignToChest(ShopCreatedContext event) {
        if (!Properties.STICK_SIGNS_TO_CHESTS || ChestShopSign.isAdminShop(event.getSignLines())) {
            return;
        }

        Block signBlock = event.getSign().getBlock();
        if (!(signBlock.getBlockData() instanceof org.bukkit.block.data.type.Sign)) {
            return;
        }

        BlockFace shopBlockFace = null;
        for (BlockFace face : ShopBlockUtil.CHEST_EXTENSION_FACES) {
            if (ShopBlockUtil.couldBeShopContainer(signBlock.getRelative(face))) {
                shopBlockFace = face;
                break;
            }
        }
        if (shopBlockFace == null) {
            return;
        }

        int index = signBlock.getType().name().indexOf("SIGN");
        if (index < 0) {
            return;
        }
        Material newMaterial = Material.valueOf(signBlock.getType().name().substring(0, index) + "WALL_SIGN");
        signBlock.setType(newMaterial);

        Sign sign = (Sign) signBlock.getState();
        WallSign signMaterial = (WallSign) Bukkit.createBlockData(newMaterial);
        signMaterial.setFacing(shopBlockFace.getOppositeFace());
        sign.setBlockData(signMaterial);

        String[] lines = event.getSignLines();
        for (int i = 0; i < lines.length; ++i) {
            sign.setLine(i, lines[i]);
        }
        sign.update(true);
    }

    /** Notify the creator that their shop was made. */
    private void sendCreatedMessage(ShopCreatedContext event) {
        message.send(event.getPlayer(), "chestshop.SHOP_CREATED");
    }

    /** Write a shop-creation line to the shop log (off the main thread). */
    private void logCreation(ShopCreatedContext event) {
        ChestShop.runInAsyncThread(() -> {
            String creator = event.getPlayer().getName();
            String shopOwner = ChestShopSign.getOwner(event.getSignLines());
            String typeOfShop = ChestShopSign.isAdminShop(shopOwner)
                    ? "an Admin Shop"
                    : "a shop" + (event.createdByOwner() ? "" : " for " + event.getOwnerAccount().getName());
            String item = ChestShopSign.getQuantity(event.getSignLines()) + ' ' + ChestShopSign.getItem(event.getSignLines());
            String prices = ChestShopSign.getPrice(event.getSignLines());
            String location = LocationUtil.locationToString(event.getSign().getLocation());
            ChestShop.getShopLogger().info(String.format(CREATION_LOG, creator, typeOfShop, item, prices, location));
        });
    }

    /** Write a shop-removal line to the shop log (off the main thread). */
    private void logRemoval(ShopDestroyedContext event) {
        if (!Properties.LOG_ALL_SHOP_REMOVALS && event.getDestroyer() != null) {
            return;
        }
        ChestShop.runInAsyncThread(() -> {
            String shopOwner = ChestShopSign.getOwner(event.getSign());
            String typeOfShop = ChestShopSign.isAdminShop(shopOwner)
                    ? "An Admin Shop"
                    : "A shop belonging to " + shopOwner;
            String item = ChestShopSign.getQuantity(event.getSign()) + ' ' + ChestShopSign.getItem(event.getSign());
            String prices = ChestShopSign.getPrice(event.getSign());
            String location = LocationUtil.locationToString(event.getSign().getLocation());
            ChestShop.getShopLogger().info(String.format(REMOVAL_LOG,
                    typeOfShop,
                    event.getDestroyer() != null ? event.getDestroyer().getName() : "???",
                    item, prices, location));
        });
    }

    /**
     * Charge the configured creation fee to {@code player} for a shop with the given
     * sign lines. Returns {@code true} if creation may proceed (fee paid, or none is
     * due — zero price, admin shop, or the {@code NOFEE} permission), {@code false} if
     * the player could not afford it (the caller should fail the creation).
     */
    public boolean chargeCreationFee(Player player, String[] signLines) {
        BigDecimal price = Properties.SHOP_CREATION_PRICE;

        if (price.compareTo(BigDecimal.ZERO) == 0
                || ChestShopSign.isAdminShop(signLines)
                || Permissions.has(player, NOFEE)) {
            return true;
        }

        if (!economy.withdraw(player.getUniqueId(), price, player.getWorld())) {
            return false;
        }

        creditServerEconomy(price, player.getWorld());
        message.send(player, "chestshop.SHOP_FEE_PAID", "amount", economy.format(price));
        return true;
    }

    /**
     * Refund the configured removal price to the shop's owner when {@code destroyer}
     * breaks it, mirroring the deduction from the server-economy account. No-ops when
     * there is no refund due (no destroyer, {@code NOFEE}, zero price, an autofill
     * placeholder sign, or an unknown owner account).
     */
    public void refundOnRemoval(Player destroyer, Sign sign) {
        BigDecimal refund = Properties.SHOP_REFUND_PRICE;

        if (destroyer == null || Permissions.has(destroyer, NOFEE) || refund.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }

        if (ChatColor.stripColor(ChestShopSign.getItem(sign)).equals(AUTOFILL_CODE)) {
            return;
        }

        Account account = accounts.resolveAccount(ChestShopSign.getOwner(sign));
        if (account == null) {
            return;
        }

        economy.deposit(account.getUuid(), refund, sign.getWorld());

        debitServerEconomy(refund, sign.getWorld());
        message.send(destroyer, "chestshop.SHOP_REFUNDED", "amount", economy.format(refund));
    }

    /** Mirror a collected creation fee into the server-economy account, if one is configured. */
    private void creditServerEconomy(BigDecimal amount, World world) {
        Account serverAccount = accounts.getServerEconomyAccount();
        if (serverAccount != null) {
            economy.deposit(serverAccount.getUuid(), amount, world);
        }
    }

    /** Mirror an issued refund out of the server-economy account, if one is configured. */
    private void debitServerEconomy(BigDecimal amount, World world) {
        Account serverAccount = accounts.getServerEconomyAccount();
        if (serverAccount != null) {
            economy.withdraw(serverAccount.getUuid(), amount, world);
        }
    }
}
