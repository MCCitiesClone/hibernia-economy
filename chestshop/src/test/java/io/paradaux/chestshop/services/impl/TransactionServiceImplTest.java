package io.paradaux.chestshop.services.impl;

import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.listeners.MarketListener;
import io.paradaux.chestshop.listeners.RestrictedSignListener;
import io.paradaux.chestshop.listeners.SignBreakListener;
import io.paradaux.chestshop.listeners.StockCounterListener;
import io.paradaux.chestshop.model.Account;
import io.paradaux.chestshop.model.PendingTransaction;
import io.paradaux.chestshop.model.PendingTransaction.TransactionOutcome;
import io.paradaux.chestshop.model.Transaction;
import io.paradaux.chestshop.model.config.ChestShopConfiguration;
import io.paradaux.chestshop.services.AccountService;
import io.paradaux.chestshop.services.InventoryService;
import io.paradaux.chestshop.services.ItemService;
import io.paradaux.chestshop.services.MetricsService;
import io.paradaux.chestshop.services.ShopBlockService;
import io.paradaux.chestshop.services.ShopService;
import io.paradaux.chestshop.services.SignService;
import io.paradaux.hibernia.framework.i18n.Message;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.stubbing.Answer;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.paradaux.chestshop.model.Transaction.TransactionType.BUY;
import static io.paradaux.chestshop.model.Transaction.TransactionType.SELL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * End-to-end coverage for {@link TransactionServiceImpl}: {@code prepare} (trade-context
 * construction incl. shift-selling and unlimited admin shops), {@code validate} (the ordered
 * pre-trade validators + error messaging), {@code execute} (atomic goods+money settlement with
 * rollback) and {@code process} (the post-trade reaction pipeline). Every collaborator is a
 * Mockito mock except the economy, which is a {@link FakeEconomy} (its {@code bind}
 * signature references Treasury types absent from the test runtime). Static
 * {@link SignService}/{@link Bukkit}/{@link ChestShop} calls are stubbed per test.
 */
class TransactionServiceImplTest {

    private FakeEconomy economy;
    private ShopService shops;
    private AccountService accounts;
    private SignBreakListener signBreak;
    private StockCounterListener stockCounter;
    private Message message;
    private ItemService items;
    private MarketListener market;
    private ChestShopConfiguration config;
    private SignService signService;
    private ShopBlockService shopBlockService;
    private InventoryService inventoryService;
    private io.paradaux.chestshop.services.AdminBypassService adminBypass;
    private RestrictedSignListener restrictedSign;
    private MetricsService metrics;
    private PartialFillCalculator partialFill;
    private GoodsTransfer goodsTransfer;

    private TransactionServiceImpl service;

    @BeforeEach
    void setUp() {
        economy = new FakeEconomy();
        shops = mock(ShopService.class);
        accounts = mock(AccountService.class);
        signBreak = mock(SignBreakListener.class);
        stockCounter = mock(StockCounterListener.class);
        message = mock(Message.class);
        items = mock(ItemService.class);
        market = mock(MarketListener.class);
        config = mock(ChestShopConfiguration.class);
        signService = mock(SignService.class);
        shopBlockService = mock(ShopBlockService.class);
        inventoryService = mock(InventoryService.class);
        adminBypass = mock(io.paradaux.chestshop.services.AdminBypassService.class);
        restrictedSign = mock(RestrictedSignListener.class);
        metrics = mock(MetricsService.class);
        partialFill = mock(PartialFillCalculator.class);
        goodsTransfer = mock(GoodsTransfer.class);

        lenient().when(config.getValidPlayernameRegexp()).thenReturn("^[A-Za-z0-9_]{1,16}$");
        lenient().when(config.getNotificationMessageCooldown()).thenReturn(0L);
        lenient().when(config.getMaxShopAmount()).thenReturn(3840);
        lenient().when(items.getItemList(any())).thenReturn("64 Stone");
        lenient().when(items.getName(any())).thenReturn("Stone");
        // component(...) is used to build the rendered message before sendMessage; return a
        // non-null Component so recipient.sendMessage(Component) is verifiable.
        lenient().when(message.component(anyString(), any(java.util.Map.class)))
                .thenReturn(mock(net.kyori.adventure.text.Component.class));
        lenient().when(message.component(anyString(), any(Object[].class)))
                .thenReturn(mock(net.kyori.adventure.text.Component.class));

        service = new TransactionServiceImpl(economy, shops, accounts, signBreak, stockCounter, message, items, market,
                config, signService, shopBlockService, inventoryService, adminBypass, restrictedSign, metrics, partialFill, goodsTransfer);
    }

    // ── builders ──────────────────────────────────────────────────────────────

    private ItemStack item(Material type, int amount) {
        int[] amt = {amount};
        Answer<Object> behaviour = inv -> switch (inv.getMethod().getName()) {
            case "getType" -> type;
            case "getAmount" -> amt[0];
            case "setAmount" -> { amt[0] = inv.getArgument(0); yield null; }
            case "clone" -> item(type, amt[0]);
            case "toString" -> "Item(" + type + "x" + amt[0] + ")";
            case "equals" -> inv.getMock() == inv.getArgument(0);
            case "hashCode" -> System.identityHashCode(inv.getMock());
            default -> org.mockito.Mockito.RETURNS_DEFAULTS.answer(inv);
        };
        return mock(ItemStack.class, behaviour);
    }

    private Player player(String name, UUID id) {
        Player p = mock(Player.class);
        lenient().when(p.getName()).thenReturn(name);
        lenient().when(p.getUniqueId()).thenReturn(id);
        lenient().when(p.getGameMode()).thenReturn(GameMode.SURVIVAL);
        lenient().when(p.getInventory()).thenReturn(mock(PlayerInventory.class));
        return p;
    }

    private Location location(World world) {
        Location loc = mock(Location.class);
        lenient().when(loc.getWorld()).thenReturn(world);
        lenient().when(loc.getBlockX()).thenReturn(1);
        lenient().when(loc.getBlockY()).thenReturn(64);
        lenient().when(loc.getBlockZ()).thenReturn(2);
        return loc;
    }

    private Sign sign(Location loc) {
        World w = loc.getWorld(); // read before opening the s stubbing (nested mock calls corrupt it)
        Sign s = mock(Sign.class);
        lenient().when(s.getLocation()).thenReturn(loc);
        lenient().when(s.getWorld()).thenReturn(w);
        lenient().when(s.getLines()).thenReturn(new String[]{"Alice", "1", "B 5", "STONE"});
        return s;
    }

    /** A completed-trade context. */
    private Transaction txn(Transaction.TransactionType type, boolean unlimited, Sign s,
                            Player client, Account owner, Inventory ownerInv, Inventory clientInv,
                            ItemStack[] stock, BigDecimal price) {
        PendingTransaction pending = new PendingTransaction(ownerInv, clientInv, stock, price, client, owner, s, type, unlimited);
        return new Transaction(pending, s);
    }

    // ═══════════════════════════ execute() ═══════════════════════════

    @Test
    void execute_movesGoodsAndSettles_forBuy() {
        Player client = player("Notch", UUID.randomUUID());
        Account owner = new Account("Alice", "Alice", UUID.randomUUID());
        Inventory ownerInv = mock(Inventory.class);
        Inventory clientInv = mock(Inventory.class);
        Transaction event = txn(BUY, false, sign(location(mock(World.class))), client, owner, ownerInv, clientInv,
                new ItemStack[]{item(Material.STONE, 1)}, new BigDecimal("5.00"));
        when(goodsTransfer.transfer(eq(ownerInv), eq(clientInv), any())).thenReturn(true);
        economy.settleResult = true;

        service.execute(event);

        assertThat(event.isCancelled()).isFalse();
        assertThat(economy.settleCalls).isEqualTo(1);
        verify(goodsTransfer, never()).reverse(any());
    }

    @Test
    void execute_reversesGoods_whenSettlementFails() {
        Player client = player("Notch", UUID.randomUUID());
        Account owner = new Account("Alice", "Alice", UUID.randomUUID());
        Inventory ownerInv = mock(Inventory.class);
        Inventory clientInv = mock(Inventory.class);
        Transaction event = txn(SELL, false, sign(location(mock(World.class))), client, owner, ownerInv, clientInv,
                new ItemStack[]{item(Material.STONE, 1)}, new BigDecimal("5.00"));
        when(goodsTransfer.transfer(eq(clientInv), eq(ownerInv), any())).thenReturn(true);
        economy.settleResult = false;

        service.execute(event);

        assertThat(event.isCancelled()).isTrue();
        verify(goodsTransfer).reverse(event);
    }

    @Test
    void execute_cancelsOnShortfall_whenGoodsDoNotMove() {
        Player client = player("Notch", UUID.randomUUID());
        Account owner = new Account("Alice", "Alice", UUID.randomUUID());
        Inventory ownerInv = mock(Inventory.class);
        Inventory clientInv = mock(Inventory.class);
        Transaction event = txn(BUY, false, sign(location(mock(World.class))), client, owner, ownerInv, clientInv,
                new ItemStack[]{item(Material.STONE, 1)}, new BigDecimal("5.00"));
        when(goodsTransfer.transfer(any(), any(), any())).thenReturn(false);

        service.execute(event);

        assertThat(event.isCancelled()).isTrue();
        assertThat(economy.settleCalls).isZero();
    }

    @Test
    void execute_cancelsOnShortfall_logsWithNullLocation() {
        Player client = player("Notch", UUID.randomUUID());
        Account owner = new Account("Alice", "Alice", UUID.randomUUID());
        Transaction event = txn(BUY, false, null, client, owner, mock(Inventory.class), mock(Inventory.class),
                new ItemStack[]{item(Material.STONE, 1)}, new BigDecimal("5.00"));
        when(goodsTransfer.transfer(any(), any(), any())).thenReturn(false);

        service.execute(event);

        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    void execute_unlimitedAdminShop_movesViaSpawnPath() {
        Player client = player("Notch", UUID.randomUUID());
        Account owner = new Account("Server", "Server", UUID.randomUUID());
        Inventory clientInv = mock(Inventory.class);
        Transaction event = txn(BUY, true, sign(location(mock(World.class))), client, owner, null, clientInv,
                new ItemStack[]{item(Material.STONE, 1)}, new BigDecimal("5.00"));
        when(goodsTransfer.moveUnlimited(eq(clientInv), any(), eq(true))).thenReturn(true);
        economy.settleResult = true;

        service.execute(event);

        assertThat(event.isCancelled()).isFalse();
        verify(goodsTransfer).moveUnlimited(eq(clientInv), any(), eq(true));
    }

    // ═══════════════════════════ process() ═══════════════════════════

    @Test
    void process_cancelledTrade_stopsAfterStockCounter() {
        Player client = player("Notch", UUID.randomUUID());
        Account owner = new Account("Alice", "Alice", UUID.randomUUID());
        Transaction event = txn(BUY, false, sign(location(mock(World.class))), client, owner,
                mock(Inventory.class), mock(Inventory.class), new ItemStack[]{item(Material.STONE, 1)}, new BigDecimal("5.00"));
        when(goodsTransfer.transfer(any(), any(), any())).thenReturn(false); // execute cancels

        service.process(event);

        verify(stockCounter).onTransaction(event);
        verify(market, never()).onTransaction(any());
        verify(metrics, never()).onTransaction(any());
    }

    @Test
    void process_successfulTrade_runsFullReactionPipeline() {
        try (MockedStatic<ChestShop> cs = mockStatic(ChestShop.class);
             MockedStatic<Bukkit> bk = mockStatic(Bukkit.class)) {
            World world = mock(World.class);
            when(world.getName()).thenReturn("world");
            UUID ownerId = UUID.randomUUID();
            Player client = player("Notch", UUID.randomUUID());
            Account owner = new Account("Alice", "Alice", ownerId);
            Inventory ownerInv = mock(Inventory.class);
            Sign s = sign(location(world));
            Transaction event = txn(BUY, false, s, client, owner, ownerInv, mock(Inventory.class),
                    new ItemStack[]{item(Material.STONE, 1)}, new BigDecimal("5.00"));
            when(goodsTransfer.transfer(any(), any(), any())).thenReturn(true);
            economy.settleResult = true;
            when(signService.isAdminShop(s)).thenReturn(false);
            when(config.isRemoveEmptyShops()).thenReturn(false); // shop not removed
            when(config.isShowTransactionInformationClient()).thenReturn(false);
            when(config.isShowTransactionInformationOwner()).thenReturn(false);
            Map<ItemStack, Integer> counts = new LinkedHashMap<>();
            counts.put(item(Material.STONE, 1), 1);
            when(inventoryService.getItemCounts(any())).thenReturn(counts);
            // Execute the async logging runnable inline so its body (the log line) is covered.
            cs.when(() -> ChestShop.runInAsyncThread(any())).thenAnswer(inv -> {
                ((Runnable) inv.getArgument(0)).run();
                return null;
            });

            service.process(event);

            verify(market).onTransaction(event);
            verify(metrics).onTransaction(event);
            assertThat(economy.migrateCalls).isEqualTo(1);
            cs.verify(() -> ChestShop.runInAsyncThread(any()));
        }
    }

    // ═══════════════════════════ clearNotificationCooldowns ═══════════════════════════

    @Test
    void clearNotificationCooldowns_noOpWhenCooldownDisabled() {
        when(config.getNotificationMessageCooldown()).thenReturn(0L);
        service.clearNotificationCooldowns(UUID.randomUUID()); // must not throw
    }

    @Test
    void clearNotificationCooldowns_removesRows_whenEnabled() {
        when(config.getNotificationMessageCooldown()).thenReturn(5L);
        service.clearNotificationCooldowns(UUID.randomUUID());
    }

    // ═══════════════════════════ validate — individual validators ═══════════════════════════

    private PendingTransaction pending(Transaction.TransactionType type, boolean unlimited, Sign s,
                                       Player client, Account owner, Inventory ownerInv, ItemStack[] stock, BigDecimal price) {
        return new PendingTransaction(ownerInv, mock(Inventory.class), stock, price, client, owner, s, type, unlimited);
    }

    private Account owner() { return new Account("Alice", "Alice", UUID.randomUUID()); }

    @Test
    void validate_rejectsAdminShopClientName() {
        Player client = player("Server", UUID.randomUUID());
        when(signService.isAdminShop("Server")).thenReturn(true);
        PendingTransaction ctx = pending(BUY, false, sign(location(mock(World.class))), client, owner(),
                mock(Inventory.class), new ItemStack[]{item(Material.STONE, 1)}, new BigDecimal("5"));
        service.validate(ctx);
        assertThat(ctx.getTransactionOutcome()).isEqualTo(TransactionOutcome.INVALID_CLIENT_NAME);
    }

    @Test
    void validate_rejectsInvalidClientNameByRegex() {
        Player client = player("bad name!", UUID.randomUUID());
        when(signService.isAdminShop("bad name!")).thenReturn(false);
        PendingTransaction ctx = pending(BUY, false, sign(location(mock(World.class))), client, owner(),
                mock(Inventory.class), new ItemStack[]{item(Material.STONE, 1)}, new BigDecimal("5"));
        service.validate(ctx);
        assertThat(ctx.getTransactionOutcome()).isEqualTo(TransactionOutcome.INVALID_CLIENT_NAME);
    }

    @Test
    void validate_rejectsCreativeMode() {
        Player client = player("Notch", UUID.randomUUID());
        when(client.getGameMode()).thenReturn(GameMode.CREATIVE);
        when(config.isIgnoreCreativeMode()).thenReturn(true);
        try (MockedStatic<SignService> ss = mockStatic(SignService.class)) {
            ss.when(() -> SignService.getPrice(any(Sign.class))).thenReturn("b5");
            PendingTransaction ctx = pending(BUY, false, sign(location(mock(World.class))), client, owner(),
                    mock(Inventory.class), new ItemStack[]{item(Material.STONE, 1)}, new BigDecimal("5"));
            service.validate(ctx);
            assertThat(ctx.getTransactionOutcome()).isEqualTo(TransactionOutcome.CREATIVE_MODE_PROTECTION);
        }
    }

    @Test
    void validate_flagsAndRemovesFreeShop() {
        Player client = player("Notch", UUID.randomUUID());
        when(config.isAllowFreeShops()).thenReturn(false);
        Block block = mock(Block.class);
        Sign s = sign(location(mock(World.class)));
        when(s.getBlock()).thenReturn(block);
        try (MockedStatic<SignService> ss = mockStatic(SignService.class)) {
            ss.when(() -> SignService.getPrice(s)).thenReturn("b0"); // free buy price
            PendingTransaction ctx = pending(BUY, false, s, client, owner(),
                    mock(Inventory.class), new ItemStack[]{item(Material.STONE, 1)}, new BigDecimal("5"));
            service.validate(ctx);
            assertThat(ctx.getTransactionOutcome()).isEqualTo(TransactionOutcome.INVALID_SHOP);
            assertThat(ctx.isRejectedAsFreeShop()).isTrue();
            verify(signBreak).sendShopDestroyed(s, client);
            verify(block).breakNaturally();
        }
    }

    @Test
    void validate_freeShopSkipped_whenAllowFreeShopsOn() {
        Player client = player("Notch", UUID.randomUUID());
        when(config.isAllowFreeShops()).thenReturn(true);
        when(config.isAllowPartialTransactions()).thenReturn(true);
        try (MockedStatic<SignService> ss = mockStatic(SignService.class)) {
            ss.when(() -> SignService.getItem(any(Sign.class))).thenReturn("STONE");
            when(adminBypass.has(any(), any())).thenReturn(true);
            when(inventoryService.fits(any(ItemStack[].class), any())).thenReturn(true);
            PendingTransaction ctx = pending(BUY, true, sign(location(mock(World.class))), client, owner(),
                    mock(Inventory.class), new ItemStack[]{item(Material.STONE, 1)}, new BigDecimal("5"));
            service.validate(ctx);
            assertThat(ctx.isCancelled()).isFalse();
        }
    }

    @Test
    void validate_freeShop_skippedWhenSignNull() {
        Player client = player("Notch", UUID.randomUUID());
        when(config.isAllowFreeShops()).thenReturn(false);
        when(config.isAllowPartialTransactions()).thenReturn(true);
        try (MockedStatic<SignService> ss = mockStatic(SignService.class)) {
            ss.when(() -> SignService.getItem((Sign) null)).thenReturn("STONE");
            when(adminBypass.has(any(), any())).thenReturn(true);
            when(inventoryService.fits(any(ItemStack[].class), any())).thenReturn(true);
            // sign null → flagFreeShop returns early; unlimited owner so invalid-shop passes
            PendingTransaction ctx = pending(BUY, true, null, client, owner(),
                    mock(Inventory.class), new ItemStack[]{item(Material.STONE, 1)}, new BigDecimal("5"));
            service.validate(ctx);
            assertThat(ctx.isCancelled()).isFalse();
        }
    }

    @Test
    void validate_rejectsMissingPrice_forBuy() {
        Player client = player("Notch", UUID.randomUUID());
        when(config.isAllowFreeShops()).thenReturn(true);
        PendingTransaction ctx = pending(BUY, false, sign(location(mock(World.class))), client, owner(),
                mock(Inventory.class), new ItemStack[]{item(Material.STONE, 1)}, io.paradaux.chestshop.utils.PriceUtil.NO_PRICE);
        service.validate(ctx);
        assertThat(ctx.getTransactionOutcome()).isEqualTo(TransactionOutcome.SHOP_DOES_NOT_SELL_THIS_ITEM);
    }

    @Test
    void validate_rejectsMissingPrice_forSell() {
        Player client = player("Notch", UUID.randomUUID());
        when(config.isAllowFreeShops()).thenReturn(true);
        PendingTransaction ctx = pending(SELL, false, sign(location(mock(World.class))), client, owner(),
                mock(Inventory.class), new ItemStack[]{item(Material.STONE, 1)}, io.paradaux.chestshop.utils.PriceUtil.NO_PRICE);
        service.validate(ctx);
        assertThat(ctx.getTransactionOutcome()).isEqualTo(TransactionOutcome.SHOP_DOES_NOT_BUY_THIS_ITEM);
    }

    @Test
    void validate_rejectsEmptyStock() {
        Player client = player("Notch", UUID.randomUUID());
        when(config.isAllowFreeShops()).thenReturn(true);
        PendingTransaction ctx = pending(BUY, false, sign(location(mock(World.class))), client, owner(),
                mock(Inventory.class), new ItemStack[]{null}, new BigDecimal("5"));
        service.validate(ctx);
        assertThat(ctx.getTransactionOutcome()).isEqualTo(TransactionOutcome.INVALID_SHOP);
    }

    @Test
    void validate_rejectsNonAdminShopWithoutContainer() {
        Player client = player("Notch", UUID.randomUUID());
        when(config.isAllowFreeShops()).thenReturn(true);
        Sign s = sign(location(mock(World.class)));
        when(signService.isAdminShop(s)).thenReturn(false);
        PendingTransaction ctx = pending(BUY, false, s, client, owner(),
                null, new ItemStack[]{item(Material.STONE, 1)}, new BigDecimal("5"));
        service.validate(ctx);
        assertThat(ctx.getTransactionOutcome()).isEqualTo(TransactionOutcome.INVALID_SHOP);
    }

    // ── playernamePattern caching (regex change) ──
    @Test
    void validate_recompilesRegex_whenConfigChanges() {
        Player client = player("Notch", UUID.randomUUID());
        when(config.isAllowFreeShops()).thenReturn(true);
        when(config.isAllowPartialTransactions()).thenReturn(true);
        try (MockedStatic<SignService> ss = mockStatic(SignService.class)) {
            ss.when(() -> SignService.getItem(any(Sign.class))).thenReturn("STONE");
            when(adminBypass.has(any(), any())).thenReturn(true);
            when(inventoryService.fits(any(ItemStack[].class), any())).thenReturn(true);
            PendingTransaction ctx1 = pending(BUY, true, sign(location(mock(World.class))), client, owner(),
                    mock(Inventory.class), new ItemStack[]{item(Material.STONE, 1)}, new BigDecimal("5"));
            service.validate(ctx1);
            // change the configured regex → pattern must be recompiled on the next validate
            when(config.getValidPlayernameRegexp()).thenReturn("^[A-Za-z]{1,16}$");
            PendingTransaction ctx2 = pending(BUY, true, sign(location(mock(World.class))), client, owner(),
                    mock(Inventory.class), new ItemStack[]{item(Material.STONE, 1)}, new BigDecimal("5"));
            service.validate(ctx2);
            assertThat(ctx2.isCancelled()).isFalse();
        }
    }

    // ═══════════════════════════ checkFundsAndStock (partial disabled) ═══════════════════════════

    @Test
    void validate_buy_insufficientFunds() {
        Player client = player("Notch", UUID.randomUUID());
        setupWholeTradeConfig();
        economy.hasFunds = (u, a) -> false;
        PendingTransaction ctx = wholeTradeCtx(BUY, client);
        service.validate(ctx);
        assertThat(ctx.getTransactionOutcome()).isEqualTo(TransactionOutcome.CLIENT_DOES_NOT_HAVE_ENOUGH_MONEY);
    }

    @Test
    void validate_buy_outOfStock() {
        Player client = player("Notch", UUID.randomUUID());
        setupWholeTradeConfig();
        economy.hasFunds = (u, a) -> true;
        when(inventoryService.hasItems(any(), any())).thenReturn(false);
        PendingTransaction ctx = wholeTradeCtx(BUY, client);
        service.validate(ctx);
        assertThat(ctx.getTransactionOutcome()).isEqualTo(TransactionOutcome.NOT_ENOUGH_STOCK_IN_CHEST);
    }

    @Test
    void validate_sell_shopOutOfMoney() {
        Player client = player("Notch", UUID.randomUUID());
        setupWholeTradeConfig();
        economy.hasFunds = (u, a) -> false;
        PendingTransaction ctx = wholeTradeCtx(SELL, client);
        service.validate(ctx);
        assertThat(ctx.getTransactionOutcome()).isEqualTo(TransactionOutcome.SHOP_DOES_NOT_HAVE_ENOUGH_MONEY);
    }

    @Test
    void validate_sell_clientLacksItems() {
        Player client = player("Notch", UUID.randomUUID());
        setupWholeTradeConfig();
        economy.hasFunds = (u, a) -> true;
        when(inventoryService.hasItems(any(), any())).thenReturn(false);
        PendingTransaction ctx = wholeTradeCtx(SELL, client);
        service.validate(ctx);
        assertThat(ctx.getTransactionOutcome()).isEqualTo(TransactionOutcome.NOT_ENOUGH_STOCK_IN_INVENTORY);
    }

    @Test
    void validate_buy_passesAllChecks() {
        Player client = player("Notch", UUID.randomUUID());
        setupWholeTradeConfig();
        economy.hasFunds = (u, a) -> true;
        when(inventoryService.hasItems(any(), any())).thenReturn(true);
        when(inventoryService.fits(any(ItemStack[].class), any())).thenReturn(true);
        when(adminBypass.has(any(), any())).thenReturn(true);
        PendingTransaction ctx = wholeTradeCtx(BUY, client);
        service.validate(ctx);
        assertThat(ctx.isCancelled()).isFalse();
    }

    private void setupWholeTradeConfig() {
        when(config.isAllowFreeShops()).thenReturn(true);
        when(config.isAllowPartialTransactions()).thenReturn(false);
        lenient().when(adminBypass.has(any(), any())).thenReturn(true);
        lenient().when(inventoryService.fits(any(ItemStack[].class), any())).thenReturn(true);
        lenient().when(accounts.isIgnoring(any(UUID.class))).thenReturn(true); // suppress owner notifications by default
    }

    private PendingTransaction wholeTradeCtx(Transaction.TransactionType type, Player client) {
        Sign s = sign(location(mock(World.class)));
        lenient().when(signService.isAdminShop(s)).thenReturn(false);
        return pending(type, false, s, client, owner(), mock(Inventory.class),
                new ItemStack[]{item(Material.STONE, 1)}, new BigDecimal("5"));
    }

    // ═══════════════════════════ checkPermissions ═══════════════════════════

    @Test
    void validate_buy_permissionDeniedByHashNode() {
        Player client = player("Notch", UUID.randomUUID());
        setupWholeTradeConfig();
        economy.hasFunds = (u, a) -> true;
        when(inventoryService.hasItems(any(), any())).thenReturn(true);
        try (MockedStatic<SignService> ss = mockStatic(SignService.class);
             MockedStatic<io.paradaux.chestshop.utils.Permissions> perms =
                     mockStatic(io.paradaux.chestshop.utils.Permissions.class)) {
            ss.when(() -> SignService.getItem(any(Sign.class))).thenReturn("#42");
            perms.when(() -> io.paradaux.chestshop.utils.Permissions.hasPermissionSetFalse(eq(client), any()))
                    .thenReturn(true);
            PendingTransaction ctx = wholeTradeCtx(BUY, client);
            service.validate(ctx);
            assertThat(ctx.getTransactionOutcome()).isEqualTo(TransactionOutcome.CLIENT_DOES_NOT_HAVE_PERMISSION);
        }
    }

    @Test
    void validate_buy_permissionDeniedPerMaterial() {
        Player client = player("Notch", UUID.randomUUID());
        setupWholeTradeConfig();
        economy.hasFunds = (u, a) -> true;
        when(inventoryService.hasItems(any(), any())).thenReturn(true);
        when(adminBypass.has(any(), any())).thenReturn(false); // no buy permission at all
        try (MockedStatic<SignService> ss = mockStatic(SignService.class)) {
            ss.when(() -> SignService.getItem(any(Sign.class))).thenReturn("STONE");
            PendingTransaction ctx = wholeTradeCtx(BUY, client);
            service.validate(ctx);
            assertThat(ctx.getTransactionOutcome()).isEqualTo(TransactionOutcome.CLIENT_DOES_NOT_HAVE_PERMISSION);
        }
    }

    @Test
    void validate_sell_permissionGrantedPerMaterialNode() {
        Player client = player("Notch", UUID.randomUUID());
        setupWholeTradeConfig();
        economy.hasFunds = (u, a) -> true;
        when(inventoryService.hasItems(any(), any())).thenReturn(true);
        when(inventoryService.fits(any(ItemStack[].class), any())).thenReturn(true);
        // deny the base SELL node but grant the per-material node → second half of the || fires.
        when(adminBypass.has(eq(client), eq(io.paradaux.chestshop.utils.Permissions.SELL))).thenReturn(false);
        when(adminBypass.has(eq(client), eq(io.paradaux.chestshop.utils.Permissions.SELL_ID + "stone"))).thenReturn(true);
        try (MockedStatic<SignService> ss = mockStatic(SignService.class)) {
            ss.when(() -> SignService.getItem(any(Sign.class))).thenReturn("STONE");
            PendingTransaction ctx = wholeTradeCtx(SELL, client);
            service.validate(ctx);
            assertThat(ctx.isCancelled()).isFalse();
        }
    }

    // ═══════════════════════════ checkStockFits ═══════════════════════════

    @Test
    void validate_sell_noSpaceInChest() {
        Player client = player("Notch", UUID.randomUUID());
        setupWholeTradeConfig();
        economy.hasFunds = (u, a) -> true;
        when(inventoryService.hasItems(any(), any())).thenReturn(true);
        when(adminBypass.has(any(), any())).thenReturn(true);
        try (MockedStatic<SignService> ss = mockStatic(SignService.class)) {
            ss.when(() -> SignService.getItem(any(Sign.class))).thenReturn("STONE");
            when(inventoryService.fits(any(ItemStack[].class), any())).thenReturn(false); // chest full
            PendingTransaction ctx = wholeTradeCtx(SELL, client);
            when(accounts.isIgnoring(any(UUID.class))).thenReturn(true); // suppress owner notify
            service.validate(ctx);
            assertThat(ctx.getTransactionOutcome()).isEqualTo(TransactionOutcome.NOT_ENOUGH_SPACE_IN_CHEST);
        }
    }

    @Test
    void validate_buy_noSpaceInInventory() {
        Player client = player("Notch", UUID.randomUUID());
        setupWholeTradeConfig();
        economy.hasFunds = (u, a) -> true;
        when(inventoryService.hasItems(any(), any())).thenReturn(true);
        when(adminBypass.has(any(), any())).thenReturn(true);
        try (MockedStatic<SignService> ss = mockStatic(SignService.class)) {
            ss.when(() -> SignService.getItem(any(Sign.class))).thenReturn("STONE");
            when(inventoryService.fits(any(ItemStack[].class), any())).thenReturn(false);
            PendingTransaction ctx = wholeTradeCtx(BUY, client);
            service.validate(ctx);
            assertThat(ctx.getTransactionOutcome()).isEqualTo(TransactionOutcome.NOT_ENOUGH_SPACE_IN_INVENTORY);
        }
    }

    @Test
    void validate_sell_unlimitedOwner_skipsChestSpaceCheck() {
        Player client = player("Notch", UUID.randomUUID());
        setupWholeTradeConfig();
        economy.hasFunds = (u, a) -> true;
        when(inventoryService.hasItems(any(), any())).thenReturn(true);
        when(adminBypass.has(any(), any())).thenReturn(true);
        try (MockedStatic<SignService> ss = mockStatic(SignService.class)) {
            ss.when(() -> SignService.getItem(any(Sign.class))).thenReturn("STONE");
            Sign s = sign(location(mock(World.class)));
            when(signService.isAdminShop(s)).thenReturn(true); // unlimited owner ⇒ admin shop
            PendingTransaction ctx = pending(SELL, true, s, client, owner(), null,
                    new ItemStack[]{item(Material.STONE, 1)}, new BigDecimal("5"));
            service.validate(ctx);
            assertThat(ctx.isCancelled()).isFalse();
        }
    }

    // ═══════════════════════════ sendErrorMessage (outcome injected via partialFill) ═══════════════════════════

    /** Reach {@code sendErrorMessage} with the given outcome by having partialFill cancel the ctx. */
    private PendingTransaction injectOutcome(TransactionOutcome outcome, Player client, Account owner, Inventory ownerInv) {
        when(config.isAllowFreeShops()).thenReturn(true);
        when(config.isAllowPartialTransactions()).thenReturn(true);
        doAnswer(inv -> { ((PendingTransaction) inv.getArgument(0)).setCancelled(outcome); return null; })
                .when(partialFill).adjustBuy(any());
        Sign s = sign(location(mock(World.class)));
        return pending(BUY, false, s, client, owner, ownerInv, new ItemStack[]{item(Material.STONE, 1)}, new BigDecimal("5"));
    }

    @Test
    void sendError_clientDepositFailed() {
        Player client = player("Notch", UUID.randomUUID());
        PendingTransaction ctx = injectOutcome(TransactionOutcome.CLIENT_DEPOSIT_FAILED, client, owner(), mock(Inventory.class));
        service.validate(ctx);
        verify(message).send(client, "chestshop.CLIENT_DEPOSIT_FAILED");
    }

    @Test
    void sendError_shopIsRestricted() {
        Player client = player("Notch", UUID.randomUUID());
        PendingTransaction ctx = injectOutcome(TransactionOutcome.SHOP_IS_RESTRICTED, client, owner(), mock(Inventory.class));
        service.validate(ctx);
        verify(message).send(client, "chestshop.ACCESS_DENIED");
    }

    @Test
    void sendError_defaultOutcome_sendsNothing() {
        Player client = player("Notch", UUID.randomUUID());
        PendingTransaction ctx = injectOutcome(TransactionOutcome.SPAM_CLICKING_PROTECTION, client, owner(), mock(Inventory.class));
        service.validate(ctx);
        verify(message, never()).send(any(org.bukkit.command.CommandSender.class), anyString());
    }

    @Test
    void sendError_shopDepositFailed_notifiesOwnerToo() {
        try (MockedStatic<Bukkit> bk = mockStatic(Bukkit.class)) {
            UUID ownerId = UUID.randomUUID();
            Account owner = new Account("Alice", "Alice", ownerId);
            Player ownerPlayer = player("Alice", ownerId);
            bk.when(() -> Bukkit.getPlayer(ownerId)).thenReturn(ownerPlayer);
            Player client = player("Notch", UUID.randomUUID());
            PendingTransaction ctx = injectOutcome(TransactionOutcome.SHOP_DEPOSIT_FAILED, client, owner, mock(Inventory.class));
            service.validate(ctx);
            verify(message).send(client, "chestshop.SHOP_DEPOSIT_FAILED");
            verify(ownerPlayer).sendMessage(any(net.kyori.adventure.text.Component.class));
        }
    }

    // ═══════════════════════════ sendShopLocationMessage / sendMessageToOwner ═══════════════════════════

    @Test
    void sendError_notEnoughSpaceInChest_notifiesOnlineOwner_worldNamed() {
        try (MockedStatic<Bukkit> bk = mockStatic(Bukkit.class)) {
            World world = mock(World.class);
            when(world.getName()).thenReturn("world");
            UUID ownerId = UUID.randomUUID();
            Account owner = new Account("Alice", "Alice", ownerId);
            Player ownerPlayer = player("Alice", ownerId);
            bk.when(() -> Bukkit.getPlayer(ownerId)).thenReturn(ownerPlayer);
            when(accounts.isIgnoring(ownerId)).thenReturn(false); // owner wants the message
            Player client = player("Notch", UUID.randomUUID());
            Sign s = sign(location(world));
            when(config.isAllowFreeShops()).thenReturn(true);
            when(config.isAllowPartialTransactions()).thenReturn(true);
            doAnswer(inv -> { ((PendingTransaction) inv.getArgument(0)).setCancelled(TransactionOutcome.NOT_ENOUGH_SPACE_IN_CHEST); return null; })
                    .when(partialFill).adjustBuy(any());
            PendingTransaction ctx = pending(BUY, false, s, client, owner, mock(Inventory.class),
                    new ItemStack[]{item(Material.STONE, 1)}, new BigDecimal("5"));

            service.validate(ctx);

            verify(ownerPlayer).sendMessage(any(net.kyori.adventure.text.Component.class));
            verify(message).send(client, "chestshop.NOT_ENOUGH_SPACE_IN_CHEST");
        }
    }

    @Test
    void sendError_notEnoughStockInChest_ownerOffline_worldNull() {
        try (MockedStatic<Bukkit> bk = mockStatic(Bukkit.class)) {
            UUID ownerId = UUID.randomUUID();
            Account owner = new Account("Alice", "Alice", ownerId);
            bk.when(() -> Bukkit.getPlayer(ownerId)).thenReturn(null); // owner offline → sendMessageToOwner returns early
            when(accounts.isIgnoring(ownerId)).thenReturn(false);
            Player client = player("Notch", UUID.randomUUID());
            Sign s = sign(location(null)); // world unloaded → "?" placeholder
            when(config.isAllowFreeShops()).thenReturn(true);
            when(config.isAllowPartialTransactions()).thenReturn(true);
            doAnswer(inv -> { ((PendingTransaction) inv.getArgument(0)).setCancelled(TransactionOutcome.NOT_ENOUGH_STOCK_IN_CHEST); return null; })
                    .when(partialFill).adjustBuy(any());
            PendingTransaction ctx = pending(BUY, false, s, client, owner, mock(Inventory.class),
                    new ItemStack[]{item(Material.STONE, 1)}, new BigDecimal("5"));

            service.validate(ctx);

            verify(message).send(client, "chestshop.NOT_ENOUGH_STOCK");
        }
    }

    @Test
    void sendError_ownerNotification_respectsCooldown() {
        try (MockedStatic<Bukkit> bk = mockStatic(Bukkit.class)) {
            World world = mock(World.class);
            when(world.getName()).thenReturn("world");
            UUID ownerId = UUID.randomUUID();
            Account owner = new Account("Alice", "Alice", ownerId);
            Player ownerPlayer = player("Alice", ownerId);
            bk.when(() -> Bukkit.getPlayer(ownerId)).thenReturn(ownerPlayer);
            when(accounts.isIgnoring(ownerId)).thenReturn(false);
            when(config.getNotificationMessageCooldown()).thenReturn(60L); // 60s window
            Player client = player("Notch", UUID.randomUUID());
            when(config.isAllowFreeShops()).thenReturn(true);
            when(config.isAllowPartialTransactions()).thenReturn(true);
            doAnswer(inv -> { ((PendingTransaction) inv.getArgument(0)).setCancelled(TransactionOutcome.NOT_ENOUGH_SPACE_IN_CHEST); return null; })
                    .when(partialFill).adjustBuy(any());

            PendingTransaction ctx1 = pending(BUY, false, sign(location(world)), client, owner, mock(Inventory.class),
                    new ItemStack[]{item(Material.STONE, 1)}, new BigDecimal("5"));
            service.validate(ctx1);
            PendingTransaction ctx2 = pending(BUY, false, sign(location(world)), client, owner, mock(Inventory.class),
                    new ItemStack[]{item(Material.STONE, 1)}, new BigDecimal("5"));
            service.validate(ctx2);

            // First notify sent; the second is swallowed by the cooldown.
            verify(ownerPlayer, org.mockito.Mockito.times(1)).sendMessage(any(net.kyori.adventure.text.Component.class));
        }
    }

    @Test
    void sendError_notEnoughSpaceInChest_suppressedWhenOwnerIgnoresAndConfigOff() {
        Player client = player("Notch", UUID.randomUUID());
        Account owner = owner();
        when(accounts.isIgnoring(owner.getUuid())).thenReturn(true);
        when(config.isShowMessageFullShop()).thenReturn(false);
        PendingTransaction ctx = injectOutcome(TransactionOutcome.NOT_ENOUGH_SPACE_IN_CHEST, client, owner, mock(Inventory.class));
        service.validate(ctx);
        verify(message).send(client, "chestshop.NOT_ENOUGH_SPACE_IN_CHEST");
    }

    @Test
    void sendError_notEnoughStockInChest_suppressedWhenOwnerIgnoresAndConfigOff() {
        Player client = player("Notch", UUID.randomUUID());
        Account owner = owner();
        when(accounts.isIgnoring(owner.getUuid())).thenReturn(true);
        when(config.isShowMessageOutOfStock()).thenReturn(false);
        PendingTransaction ctx = injectOutcome(TransactionOutcome.NOT_ENOUGH_STOCK_IN_CHEST, client, owner, mock(Inventory.class));
        service.validate(ctx);
        verify(message).send(client, "chestshop.NOT_ENOUGH_STOCK");
    }

    // ═══════════════════════════ sendTransactionMessages / sendTradeMessage (via process) ═══════════════════════════

    private Transaction successfulBuy(MockedStatic<ChestShop> cs, Sign s, Player client, Account owner, UUID ownerId) {
        Transaction event = txn(BUY, false, s, client, owner, mock(Inventory.class), mock(Inventory.class),
                new ItemStack[]{item(Material.STONE, 1)}, new BigDecimal("5.00"));
        when(goodsTransfer.transfer(any(), any(), any())).thenReturn(true);
        economy.settleResult = true;
        lenient().when(signService.isAdminShop(s)).thenReturn(false);
        lenient().when(config.isRemoveEmptyShops()).thenReturn(false);
        Map<ItemStack, Integer> counts = new LinkedHashMap<>();
        counts.put(item(Material.STONE, 1), 1);
        lenient().when(inventoryService.getItemCounts(any())).thenReturn(counts);
        cs.when(() -> ChestShop.runInAsyncThread(any())).thenAnswer(inv -> null);
        return event;
    }

    @Test
    void process_notifiesClientAndOnlineOwner() {
        try (MockedStatic<ChestShop> cs = mockStatic(ChestShop.class);
             MockedStatic<Bukkit> bk = mockStatic(Bukkit.class)) {
            World world = mock(World.class);
            when(world.getName()).thenReturn("world");
            UUID ownerId = UUID.randomUUID();
            Player client = player("Notch", UUID.randomUUID());
            Account owner = new Account("Alice", "Alice", ownerId);
            Player ownerPlayer = player("Alice", ownerId);
            Sign s = sign(location(world));
            Transaction event = successfulBuy(cs, s, client, owner, ownerId);
            when(config.isShowTransactionInformationClient()).thenReturn(true);
            when(config.isShowTransactionInformationOwner()).thenReturn(true);
            when(accounts.isIgnoring(ownerId)).thenReturn(false);
            bk.when(() -> Bukkit.getPlayer(ownerId)).thenReturn(ownerPlayer);

            service.process(event);

            verify(client).sendMessage(any(net.kyori.adventure.text.Component.class));
            verify(ownerPlayer).sendMessage(any(net.kyori.adventure.text.Component.class));
        }
    }

    @Test
    void process_ownerMessageSkipped_whenIgnoring() {
        try (MockedStatic<ChestShop> cs = mockStatic(ChestShop.class);
             MockedStatic<Bukkit> bk = mockStatic(Bukkit.class)) {
            World world = mock(World.class);
            when(world.getName()).thenReturn("world");
            UUID ownerId = UUID.randomUUID();
            Player client = player("Notch", UUID.randomUUID());
            Account owner = new Account("Alice", "Alice", ownerId);
            Sign s = sign(location(world));
            Transaction event = successfulBuy(cs, s, client, owner, ownerId);
            when(config.isShowTransactionInformationClient()).thenReturn(true);
            when(config.isShowTransactionInformationOwner()).thenReturn(true);
            when(accounts.isIgnoring(ownerId)).thenReturn(true); // owner muted → skip owner notify

            service.process(event);

            verify(client).sendMessage(any(net.kyori.adventure.text.Component.class));
        }
    }

    @Test
    void process_ownerOffline_tradeMessageReturnsEarly() {
        try (MockedStatic<ChestShop> cs = mockStatic(ChestShop.class);
             MockedStatic<Bukkit> bk = mockStatic(Bukkit.class)) {
            World world = mock(World.class);
            when(world.getName()).thenReturn("world");
            UUID ownerId = UUID.randomUUID();
            Player client = player("Notch", UUID.randomUUID());
            Account owner = new Account("Alice", "Alice", ownerId);
            Sign s = sign(location(world));
            Transaction event = successfulBuy(cs, s, client, owner, ownerId);
            when(config.isShowTransactionInformationClient()).thenReturn(false); // skip client branch
            when(config.isShowTransactionInformationOwner()).thenReturn(true);
            when(accounts.isIgnoring(ownerId)).thenReturn(false);
            bk.when(() -> Bukkit.getPlayer(ownerId)).thenReturn(null); // owner offline → sendTradeMessage returns

            service.process(event);

            verify(market).onTransaction(event); // pipeline still completed
        }
    }

    // ═══════════════════════════ deleteEmptyShop (via process) ═══════════════════════════

    private Transaction deletableBuy(MockedStatic<ChestShop> cs, Sign s, Inventory ownerInv, Account owner) {
        Transaction event = txn(BUY, false, s, player("Notch", UUID.randomUUID()), owner, ownerInv, mock(Inventory.class),
                new ItemStack[]{item(Material.STONE, 1)}, new BigDecimal("5.00"));
        when(goodsTransfer.transfer(any(), any(), any())).thenReturn(true);
        economy.settleResult = true;
        lenient().when(config.isShowTransactionInformationClient()).thenReturn(false);
        lenient().when(config.isShowTransactionInformationOwner()).thenReturn(false);
        Map<ItemStack, Integer> counts = new LinkedHashMap<>();
        counts.put(item(Material.STONE, 1), 1);
        lenient().when(inventoryService.getItemCounts(any())).thenReturn(counts);
        cs.when(() -> ChestShop.runInAsyncThread(any())).thenAnswer(inv -> null);
        return event;
    }

    @Test
    void deleteEmptyShop_sellTrade_isNoOp() {
        try (MockedStatic<ChestShop> cs = mockStatic(ChestShop.class);
             MockedStatic<Bukkit> bk = mockStatic(Bukkit.class)) {
            Sign s = sign(location(mock(World.class)));
            Account owner = new Account("Alice", "Alice", UUID.randomUUID());
            Transaction event = txn(SELL, false, s, player("Notch", UUID.randomUUID()), owner,
                    mock(Inventory.class), mock(Inventory.class), new ItemStack[]{item(Material.STONE, 1)}, new BigDecimal("5"));
            when(goodsTransfer.transfer(any(), any(), any())).thenReturn(true);
            economy.settleResult = true;
            when(config.isShowTransactionInformationClient()).thenReturn(false);
            when(config.isShowTransactionInformationOwner()).thenReturn(false);
            Map<ItemStack, Integer> counts = new LinkedHashMap<>();
            counts.put(item(Material.STONE, 1), 1);
            when(inventoryService.getItemCounts(any())).thenReturn(counts);
            cs.when(() -> ChestShop.runInAsyncThread(any())).thenAnswer(inv -> null);

            service.process(event);

            verify(shops, never()).onDestroyed(any());
        }
    }

    @Test
    void deleteEmptyShop_adminShop_isNoOp() {
        try (MockedStatic<ChestShop> cs = mockStatic(ChestShop.class);
             MockedStatic<Bukkit> bk = mockStatic(Bukkit.class)) {
            Sign s = sign(location(mock(World.class)));
            Account owner = new Account("Server", "Server", UUID.randomUUID());
            Transaction event = deletableBuy(cs, s, mock(Inventory.class), owner);
            when(signService.isAdminShop(s)).thenReturn(true); // admin shops are never removed

            service.process(event);

            verify(shops, never()).onDestroyed(any());
        }
    }

    @Test
    void deleteEmptyShop_notRemoved_whenShopStillHasStock() {
        try (MockedStatic<ChestShop> cs = mockStatic(ChestShop.class);
             MockedStatic<Bukkit> bk = mockStatic(Bukkit.class)) {
            Sign s = sign(location(mock(World.class)));
            Inventory ownerInv = mock(Inventory.class);
            Account owner = new Account("Alice", "Alice", UUID.randomUUID());
            Transaction event = deletableBuy(cs, s, ownerInv, owner);
            when(signService.isAdminShop(s)).thenReturn(false);
            when(config.isRemoveEmptyShops()).thenReturn(true);
            when(config.isAllowPartialTransactions()).thenReturn(true);
            when(ownerInv.containsAtLeast(any(), eq(1))).thenReturn(true); // still has stock → keep

            service.process(event);

            verify(shops, never()).onDestroyed(any());
        }
    }

    @Test
    void deleteEmptyShop_removesShop_andEmptiesChest() {
        try (MockedStatic<ChestShop> cs = mockStatic(ChestShop.class);
             MockedStatic<Bukkit> bk = mockStatic(Bukkit.class)) {
            World world = mock(World.class);
            when(world.getName()).thenReturn("world");
            Sign s = sign(location(world));
            when(s.getType()).thenReturn(Material.OAK_SIGN);
            Block signBlock = mock(Block.class);
            when(s.getBlock()).thenReturn(signBlock);
            Inventory ownerInv = mock(Inventory.class);
            when(ownerInv.getStorageContents()).thenReturn(new ItemStack[]{null}); // empty chest
            Account owner = new Account("Alice", "Alice", UUID.randomUUID());
            Transaction event = deletableBuy(cs, s, ownerInv, owner);
            when(signService.isAdminShop(s)).thenReturn(false);
            when(config.isRemoveEmptyShops()).thenReturn(true);
            when(config.isAllowPartialTransactions()).thenReturn(true);
            when(ownerInv.containsAtLeast(any(), eq(1))).thenReturn(false); // out of stock
            when(config.getRemoveEmptyWorlds()).thenReturn(java.util.Collections.emptySet());
            when(config.isRemoveEmptyChests()).thenReturn(true);
            Container container = mock(Container.class);
            Block containerBlock = mock(Block.class);
            when(container.getBlock()).thenReturn(containerBlock);
            when(shopBlockService.findConnectedContainer(s)).thenReturn(container);

            service.process(event);

            verify(shops).onDestroyed(any());
            verify(signBlock).setType(Material.AIR);
            verify(containerBlock).setType(Material.AIR);
        }
    }

    // NOTE: deleteEmptyShop's keep-chest branch (config REMOVE_EMPTY_CHESTS=false) calls
    // Material#isItem() and `new ItemStack(signType, 1)`, both of which require the live Bukkit
    // RegistryAccess ("No RegistryAccess implementation found" headless). That branch — the
    // `!signType.isItem()` / WALL_ normalisation / addItem / warn block — is therefore
    // unreachable in a headless unit test and is the sole uncovered region of this class.

    @Test
    void deleteEmptyShop_notRemoved_whenShopHasStock_partialDisabled() {
        try (MockedStatic<ChestShop> cs = mockStatic(ChestShop.class);
             MockedStatic<Bukkit> bk = mockStatic(Bukkit.class)) {
            Sign s = sign(location(mock(World.class)));
            Inventory ownerInv = mock(Inventory.class);
            Account owner = new Account("Alice", "Alice", UUID.randomUUID());
            Transaction event = deletableBuy(cs, s, ownerInv, owner);
            when(signService.isAdminShop(s)).thenReturn(false);
            when(config.isRemoveEmptyShops()).thenReturn(true);
            when(config.isAllowPartialTransactions()).thenReturn(false);
            when(inventoryService.hasItems(any(), eq(ownerInv))).thenReturn(true); // still has stock → keep

            service.process(event);

            verify(shops, never()).onDestroyed(any());
        }
    }

    @Test
    void deleteEmptyShop_notInRemoveWorld_isNoOp() {
        try (MockedStatic<ChestShop> cs = mockStatic(ChestShop.class);
             MockedStatic<Bukkit> bk = mockStatic(Bukkit.class)) {
            World world = mock(World.class);
            when(world.getName()).thenReturn("nether");
            Sign s = sign(location(world));
            Inventory ownerInv = mock(Inventory.class);
            Account owner = new Account("Alice", "Alice", UUID.randomUUID());
            Transaction event = deletableBuy(cs, s, ownerInv, owner);
            when(signService.isAdminShop(s)).thenReturn(false);
            when(config.isRemoveEmptyShops()).thenReturn(true);
            when(config.isAllowPartialTransactions()).thenReturn(false);
            when(inventoryService.hasItems(any(), eq(ownerInv))).thenReturn(false);
            when(config.getRemoveEmptyWorlds()).thenReturn(java.util.Collections.singleton("world")); // not "nether"

            service.process(event);

            verify(shops, never()).onDestroyed(any());
        }
    }

    // ═══════════════════════════ prepare ═══════════════════════════

    private Sign prepSign() {
        Sign s = sign(location(mock(World.class)));
        return s;
    }

    @Test
    void prepare_playerNotFound_returnsNull() {
        try (MockedStatic<SignService> ss = mockStatic(SignService.class)) {
            Sign s = prepSign();
            ss.when(() -> SignService.getOwner(s)).thenReturn("Ghost");
            when(accounts.resolveAccount("Ghost")).thenReturn(null);
            assertThat(service.prepare(s, player("Notch", UUID.randomUUID()), Action.RIGHT_CLICK_BLOCK)).isNull();
            verify(message).send(any(org.bukkit.command.CommandSender.class), eq("chestshop.PLAYER_NOT_FOUND"));
        }
    }

    @Test
    void prepare_noEconomyAccount_returnsNull() {
        try (MockedStatic<SignService> ss = mockStatic(SignService.class)) {
            Sign s = prepSign();
            ss.when(() -> SignService.getOwner(s)).thenReturn("Alice");
            Account acc = new Account("Alice", "Alice", UUID.randomUUID());
            when(accounts.resolveAccount("Alice")).thenReturn(acc);
            when(signService.isAdminShop(s)).thenReturn(false);
            economy.hasAccount = false;
            assertThat(service.prepare(s, player("Notch", UUID.randomUUID()), Action.RIGHT_CLICK_BLOCK)).isNull();
            verify(message).send(any(org.bukkit.command.CommandSender.class), eq("chestshop.NO_ECONOMY_ACCOUNT"));
        }
    }

    @Test
    void prepare_invalidItem_returnsNull() {
        try (MockedStatic<SignService> ss = mockStatic(SignService.class)) {
            Sign s = prepSign();
            ss.when(() -> SignService.getOwner(s)).thenReturn("Alice");
            ss.when(() -> SignService.getPrice(s)).thenReturn("b5:s3");
            ss.when(() -> SignService.getItem(s)).thenReturn("NOPE");
            Account acc = new Account("Alice", "Alice", UUID.randomUUID());
            when(accounts.resolveAccount("Alice")).thenReturn(acc);
            when(signService.isAdminShop(s)).thenReturn(false);
            economy.hasAccount = true;
            when(items.parse("NOPE")).thenReturn(null);
            assertThat(service.prepare(s, player("Notch", UUID.randomUUID()), Action.RIGHT_CLICK_BLOCK)).isNull();
            verify(message).send(any(org.bukkit.command.CommandSender.class), eq("chestshop.INVALID_SHOP_DETECTED"));
        }
    }

    @Test
    void prepare_quantityUnparseable_returnsNull() {
        try (MockedStatic<SignService> ss = mockStatic(SignService.class)) {
            Sign s = prepSign();
            ss.when(() -> SignService.getOwner(s)).thenReturn("Alice");
            ss.when(() -> SignService.getPrice(s)).thenReturn("b5:s3");
            ss.when(() -> SignService.getItem(s)).thenReturn("STONE");
            ss.when(() -> SignService.getQuantity(s)).thenThrow(new NumberFormatException("nan"));
            Account acc = new Account("Alice", "Alice", UUID.randomUUID());
            when(accounts.resolveAccount("Alice")).thenReturn(acc);
            when(signService.isAdminShop(s)).thenReturn(false);
            economy.hasAccount = true;
            when(items.parse("STONE")).thenReturn(item(Material.STONE, 1));
            when(shopBlockService.findConnectedContainer(s)).thenReturn(mock(Container.class));
            assertThat(service.prepare(s, player("Notch", UUID.randomUUID()), Action.RIGHT_CLICK_BLOCK)).isNull();
            verify(message).send(any(org.bukkit.command.CommandSender.class), eq("chestshop.INVALID_SHOP_PRICE"));
        }
    }

    @Test
    void prepare_quantityTooLarge_returnsNull() {
        try (MockedStatic<SignService> ss = mockStatic(SignService.class)) {
            Sign s = prepSign();
            ss.when(() -> SignService.getOwner(s)).thenReturn("Alice");
            ss.when(() -> SignService.getPrice(s)).thenReturn("b5:s3");
            ss.when(() -> SignService.getItem(s)).thenReturn("STONE");
            ss.when(() -> SignService.getQuantity(s)).thenReturn(999999); // > maxShopAmount
            Account acc = new Account("Alice", "Alice", UUID.randomUUID());
            when(accounts.resolveAccount("Alice")).thenReturn(acc);
            when(signService.isAdminShop(s)).thenReturn(false);
            economy.hasAccount = true;
            when(items.parse("STONE")).thenReturn(item(Material.STONE, 1));
            when(shopBlockService.findConnectedContainer(s)).thenReturn(mock(Container.class));
            assertThat(service.prepare(s, player("Notch", UUID.randomUUID()), Action.RIGHT_CLICK_BLOCK)).isNull();
        }
    }

    @Test
    void prepare_normalBuy_buildsContext() {
        try (MockedStatic<SignService> ss = mockStatic(SignService.class)) {
            Sign s = prepSign();
            ss.when(() -> SignService.getOwner(s)).thenReturn("Alice");
            ss.when(() -> SignService.getPrice(s)).thenReturn("b5:s3");
            ss.when(() -> SignService.getItem(s)).thenReturn("STONE");
            ss.when(() -> SignService.getQuantity(s)).thenReturn(1);
            Account acc = new Account("Alice", "Alice", UUID.randomUUID());
            when(accounts.resolveAccount("Alice")).thenReturn(acc);
            when(signService.isAdminShop(s)).thenReturn(false);
            economy.hasAccount = true;
            ItemStack parsed = item(Material.STONE, 1);
            when(items.parse("STONE")).thenReturn(parsed);
            Container container = mock(Container.class);
            Inventory ownerInv = mock(Inventory.class);
            when(container.getInventory()).thenReturn(ownerInv);
            when(shopBlockService.findConnectedContainer(s)).thenReturn(container);
            when(inventoryService.getItemsStacked(parsed)).thenReturn(new ItemStack[]{parsed});

            PendingTransaction ctx = service.prepare(s, player("Notch", UUID.randomUUID()), Action.RIGHT_CLICK_BLOCK);

            assertThat(ctx).isNotNull();
            assertThat(ctx.getTransactionType()).isEqualTo(BUY);
            assertThat(ctx.getExactPrice()).isEqualByComparingTo("5");
            assertThat(ctx.isUnlimitedOwner()).isFalse();
        }
    }

    @Test
    void prepare_sellWithReverseButtons_buildsSellContext() {
        try (MockedStatic<SignService> ss = mockStatic(SignService.class)) {
            Sign s = prepSign();
            ss.when(() -> SignService.getOwner(s)).thenReturn("Alice");
            ss.when(() -> SignService.getPrice(s)).thenReturn("b5:s3");
            ss.when(() -> SignService.getItem(s)).thenReturn("STONE");
            ss.when(() -> SignService.getQuantity(s)).thenReturn(1);
            Account acc = new Account("Alice", "Alice", UUID.randomUUID());
            when(accounts.resolveAccount("Alice")).thenReturn(acc);
            when(signService.isAdminShop(s)).thenReturn(false);
            economy.hasAccount = true;
            ItemStack parsed = item(Material.STONE, 1);
            when(items.parse("STONE")).thenReturn(parsed);
            when(config.isReverseButtons()).thenReturn(true); // buy is LEFT; RIGHT_CLICK becomes SELL
            Container container = mock(Container.class);
            when(container.getInventory()).thenReturn(mock(Inventory.class));
            when(shopBlockService.findConnectedContainer(s)).thenReturn(container);
            when(inventoryService.getItemsStacked(parsed)).thenReturn(new ItemStack[]{parsed});

            PendingTransaction ctx = service.prepare(s, player("Notch", UUID.randomUUID()), Action.RIGHT_CLICK_BLOCK);

            assertThat(ctx.getTransactionType()).isEqualTo(SELL);
            assertThat(ctx.getExactPrice()).isEqualByComparingTo("3");
        }
    }

    @Test
    void prepare_unlimitedAdminShop_noContainer() {
        try (MockedStatic<SignService> ss = mockStatic(SignService.class)) {
            Sign s = prepSign();
            ss.when(() -> SignService.getOwner(s)).thenReturn("Server");
            ss.when(() -> SignService.getPrice(s)).thenReturn("b5:s3");
            ss.when(() -> SignService.getItem(s)).thenReturn("STONE");
            ss.when(() -> SignService.getQuantity(s)).thenReturn(1);
            Account acc = new Account("Server", "Server", UUID.randomUUID());
            when(accounts.resolveAccount("Server")).thenReturn(acc);
            when(signService.isAdminShop(s)).thenReturn(true);
            ItemStack parsed = item(Material.STONE, 1);
            when(items.parse("STONE")).thenReturn(parsed);
            when(shopBlockService.findConnectedContainer(s)).thenReturn(null); // no chest
            when(inventoryService.getItemsStacked(parsed)).thenReturn(new ItemStack[]{parsed});

            PendingTransaction ctx = service.prepare(s, player("Notch", UUID.randomUUID()), Action.RIGHT_CLICK_BLOCK);

            assertThat(ctx.isUnlimitedOwner()).isTrue();
        }
    }

    @Test
    void prepare_forcedUnlimitedAdminShop_withContainer() {
        try (MockedStatic<SignService> ss = mockStatic(SignService.class)) {
            Sign s = prepSign();
            ss.when(() -> SignService.getOwner(s)).thenReturn("Server");
            ss.when(() -> SignService.getPrice(s)).thenReturn("b5:s3");
            ss.when(() -> SignService.getItem(s)).thenReturn("STONE");
            ss.when(() -> SignService.getQuantity(s)).thenReturn(1);
            Account acc = new Account("Server", "Server", UUID.randomUUID());
            when(accounts.resolveAccount("Server")).thenReturn(acc);
            when(signService.isAdminShop(s)).thenReturn(true);
            ItemStack parsed = item(Material.STONE, 1);
            when(items.parse("STONE")).thenReturn(parsed);
            Container container = mock(Container.class);
            when(container.getInventory()).thenReturn(mock(Inventory.class));
            when(shopBlockService.findConnectedContainer(s)).thenReturn(container);
            when(config.isForceUnlimitedAdminShop()).thenReturn(true);
            when(inventoryService.getItemsStacked(parsed)).thenReturn(new ItemStack[]{parsed});

            PendingTransaction ctx = service.prepare(s, player("Notch", UUID.randomUUID()), Action.RIGHT_CLICK_BLOCK);

            assertThat(ctx.isUnlimitedOwner()).isTrue();
        }
    }

    // ── shift-selling in prepare ──

    @Test
    void prepare_shiftSellsInStacks_adminShop_usesMaxStackSize() {
        try (MockedStatic<SignService> ss = mockStatic(SignService.class)) {
            Sign s = prepSign();
            ss.when(() -> SignService.getOwner(s)).thenReturn("Server");
            ss.when(() -> SignService.getPrice(s)).thenReturn("b5:s3");
            ss.when(() -> SignService.getItem(s)).thenReturn("STONE");
            ss.when(() -> SignService.getQuantity(s)).thenReturn(1);
            Account acc = new Account("Server", "Server", UUID.randomUUID());
            when(accounts.resolveAccount("Server")).thenReturn(acc);
            when(signService.isAdminShop(s)).thenReturn(true);
            ItemStack parsed = item(Material.STONE, 1);
            when(items.parse("STONE")).thenReturn(parsed);
            when(shopBlockService.findConnectedContainer(s)).thenReturn(null);
            Player p = player("Notch", UUID.randomUUID());
            when(p.isSneaking()).thenReturn(true);
            when(config.isShiftSellsInStacks()).thenReturn(true);
            when(config.getShiftAllows()).thenReturn("ALL");
            when(inventoryService.getMaxStackSize(parsed)).thenReturn(64);
            when(inventoryService.getItemsStacked(parsed)).thenReturn(new ItemStack[]{parsed});

            PendingTransaction ctx = service.prepare(s, p, Action.RIGHT_CLICK_BLOCK);

            assertThat(ctx).isNotNull();
            assertThat(ctx.getExactPrice()).isEqualByComparingTo("320"); // 5/item * 64
        }
    }

    @Test
    void prepare_shiftSellsInStacks_playerShop_usesStackAmount() {
        try (MockedStatic<SignService> ss = mockStatic(SignService.class)) {
            Sign s = prepSign();
            ss.when(() -> SignService.getOwner(s)).thenReturn("Alice");
            ss.when(() -> SignService.getPrice(s)).thenReturn("b5:s3");
            ss.when(() -> SignService.getItem(s)).thenReturn("STONE");
            ss.when(() -> SignService.getQuantity(s)).thenReturn(1);
            Account acc = new Account("Alice", "Alice", UUID.randomUUID());
            when(accounts.resolveAccount("Alice")).thenReturn(acc);
            when(signService.isAdminShop(s)).thenReturn(false);
            economy.hasAccount = true;
            ItemStack parsed = item(Material.STONE, 1);
            when(items.parse("STONE")).thenReturn(parsed);
            Container container = mock(Container.class);
            Inventory ownerInv = mock(Inventory.class);
            when(container.getInventory()).thenReturn(ownerInv);
            when(shopBlockService.findConnectedContainer(s)).thenReturn(container);
            Player p = player("Notch", UUID.randomUUID());
            when(p.isSneaking()).thenReturn(true);
            when(config.isShiftSellsInStacks()).thenReturn(true);
            when(config.getShiftAllows()).thenReturn("BUY");
            // getStackAmount: BUY checks the owner chest; not full stack → getAmount
            when(ownerInv.containsAtLeast(eq(parsed), anyInt())).thenReturn(false);
            when(inventoryService.getMaxStackSize(parsed)).thenReturn(64);
            when(inventoryService.getAmount(parsed, ownerInv)).thenReturn(10);
            when(inventoryService.getItemsStacked(parsed)).thenReturn(new ItemStack[]{parsed});

            PendingTransaction ctx = service.prepare(s, p, Action.RIGHT_CLICK_BLOCK);

            assertThat(ctx.getExactPrice()).isEqualByComparingTo("50"); // 5 * 10
        }
    }

    @Test
    void prepare_shiftSellsEverything_sellUsesPlayerInventory() {
        try (MockedStatic<SignService> ss = mockStatic(SignService.class)) {
            Sign s = prepSign();
            ss.when(() -> SignService.getOwner(s)).thenReturn("Alice");
            ss.when(() -> SignService.getPrice(s)).thenReturn("b5:s3");
            ss.when(() -> SignService.getItem(s)).thenReturn("STONE");
            ss.when(() -> SignService.getQuantity(s)).thenReturn(1);
            Account acc = new Account("Alice", "Alice", UUID.randomUUID());
            when(accounts.resolveAccount("Alice")).thenReturn(acc);
            when(signService.isAdminShop(s)).thenReturn(false);
            economy.hasAccount = true;
            ItemStack parsed = item(Material.STONE, 1);
            when(items.parse("STONE")).thenReturn(parsed);
            Container container = mock(Container.class);
            when(container.getInventory()).thenReturn(mock(Inventory.class));
            when(shopBlockService.findConnectedContainer(s)).thenReturn(container);
            Player p = player("Notch", UUID.randomUUID());
            PlayerInventory pInv = (PlayerInventory) p.getInventory();
            when(p.isSneaking()).thenReturn(true);
            when(config.isShiftSellsInStacks()).thenReturn(false);
            when(config.isShiftSellsEverything()).thenReturn(true);
            when(config.getShiftAllows()).thenReturn("SELL");
            when(inventoryService.getAmount(parsed, pInv)).thenReturn(7);
            when(inventoryService.getItemsStacked(parsed)).thenReturn(new ItemStack[]{parsed});

            // LEFT_CLICK is SELL (reverseButtons default false → buy is RIGHT).
            PendingTransaction ctx = service.prepare(s, p, Action.LEFT_CLICK_BLOCK);

            assertThat(ctx.getTransactionType()).isEqualTo(SELL);
            assertThat(ctx.getExactPrice()).isEqualByComparingTo("21"); // 3 * 7
        }
    }

    @Test
    void prepare_shiftSellsEverything_buyUsesOwnerInventory() {
        try (MockedStatic<SignService> ss = mockStatic(SignService.class)) {
            Sign s = prepSign();
            ss.when(() -> SignService.getOwner(s)).thenReturn("Alice");
            ss.when(() -> SignService.getPrice(s)).thenReturn("b5:s3");
            ss.when(() -> SignService.getItem(s)).thenReturn("STONE");
            ss.when(() -> SignService.getQuantity(s)).thenReturn(1);
            Account acc = new Account("Alice", "Alice", UUID.randomUUID());
            when(accounts.resolveAccount("Alice")).thenReturn(acc);
            when(signService.isAdminShop(s)).thenReturn(false);
            economy.hasAccount = true;
            ItemStack parsed = item(Material.STONE, 1);
            when(items.parse("STONE")).thenReturn(parsed);
            Container container = mock(Container.class);
            Inventory ownerInv = mock(Inventory.class);
            when(container.getInventory()).thenReturn(ownerInv);
            when(shopBlockService.findConnectedContainer(s)).thenReturn(container);
            Player p = player("Notch", UUID.randomUUID());
            when(p.isSneaking()).thenReturn(true);
            when(config.isShiftSellsInStacks()).thenReturn(false);
            when(config.isShiftSellsEverything()).thenReturn(true);
            when(config.getShiftAllows()).thenReturn("BUY");
            when(inventoryService.getAmount(parsed, ownerInv)).thenReturn(9);
            when(inventoryService.getItemsStacked(parsed)).thenReturn(new ItemStack[]{parsed});

            PendingTransaction ctx = service.prepare(s, p, Action.RIGHT_CLICK_BLOCK);

            assertThat(ctx.getTransactionType()).isEqualTo(BUY);
            assertThat(ctx.getExactPrice()).isEqualByComparingTo("45"); // 5 * 9
        }
    }

    @Test
    void prepare_shiftSellsInStacks_notAllowedForDirection_skipsShift() {
        try (MockedStatic<SignService> ss = mockStatic(SignService.class)) {
            Sign s = prepSign();
            ss.when(() -> SignService.getOwner(s)).thenReturn("Alice");
            ss.when(() -> SignService.getPrice(s)).thenReturn("b5:s3");
            ss.when(() -> SignService.getItem(s)).thenReturn("STONE");
            ss.when(() -> SignService.getQuantity(s)).thenReturn(1);
            Account acc = new Account("Alice", "Alice", UUID.randomUUID());
            when(accounts.resolveAccount("Alice")).thenReturn(acc);
            when(signService.isAdminShop(s)).thenReturn(false);
            economy.hasAccount = true;
            ItemStack parsed = item(Material.STONE, 1);
            when(items.parse("STONE")).thenReturn(parsed);
            Container container = mock(Container.class);
            when(container.getInventory()).thenReturn(mock(Inventory.class));
            when(shopBlockService.findConnectedContainer(s)).thenReturn(container);
            Player p = player("Notch", UUID.randomUUID());
            when(p.isSneaking()).thenReturn(true);
            when(config.isShiftSellsInStacks()).thenReturn(true);
            when(config.getShiftAllows()).thenReturn("SELL"); // buy click, but only SELL allowed → skip shift
            when(inventoryService.getItemsStacked(parsed)).thenReturn(new ItemStack[]{parsed});

            PendingTransaction ctx = service.prepare(s, p, Action.RIGHT_CLICK_BLOCK);

            assertThat(ctx.getExactPrice()).isEqualByComparingTo("5"); // unchanged (no shift)
        }
    }

    @Test
    void prepare_getStackAmount_fullStackAvailable_returnsMaxStackSize() {
        try (MockedStatic<SignService> ss = mockStatic(SignService.class)) {
            Sign s = prepSign();
            ss.when(() -> SignService.getOwner(s)).thenReturn("Alice");
            ss.when(() -> SignService.getPrice(s)).thenReturn("b5:s3");
            ss.when(() -> SignService.getItem(s)).thenReturn("STONE");
            ss.when(() -> SignService.getQuantity(s)).thenReturn(1);
            Account acc = new Account("Alice", "Alice", UUID.randomUUID());
            when(accounts.resolveAccount("Alice")).thenReturn(acc);
            when(signService.isAdminShop(s)).thenReturn(false);
            economy.hasAccount = true;
            ItemStack parsed = item(Material.STONE, 1);
            when(items.parse("STONE")).thenReturn(parsed);
            Container container = mock(Container.class);
            Inventory ownerInv = mock(Inventory.class);
            when(container.getInventory()).thenReturn(ownerInv);
            when(shopBlockService.findConnectedContainer(s)).thenReturn(container);
            Player p = player("Notch", UUID.randomUUID());
            when(p.isSneaking()).thenReturn(true);
            when(config.isShiftSellsInStacks()).thenReturn(true);
            when(config.getShiftAllows()).thenReturn("ALL");
            when(inventoryService.getMaxStackSize(parsed)).thenReturn(64);
            when(ownerInv.containsAtLeast(parsed, 64)).thenReturn(true); // a full stack is present
            when(inventoryService.getItemsStacked(parsed)).thenReturn(new ItemStack[]{parsed});

            PendingTransaction ctx = service.prepare(s, p, Action.RIGHT_CLICK_BLOCK);

            assertThat(ctx.getExactPrice()).isEqualByComparingTo("320"); // 5 * 64
        }
    }

    // ═══════════════════════════ remaining branch fills ═══════════════════════════

    @Test
    void validate_preCancelledCtx_shortCircuitsEveryValidator() {
        Player client = player("Notch", UUID.randomUUID());
        PendingTransaction ctx = pending(BUY, false, sign(location(mock(World.class))), client, owner(),
                mock(Inventory.class), new ItemStack[]{item(Material.STONE, 1)}, new BigDecimal("5"));
        ctx.setCancelled(TransactionOutcome.OTHER); // already cancelled ⇒ each validator returns early
        service.validate(ctx);
        assertThat(ctx.getTransactionOutcome()).isEqualTo(TransactionOutcome.OTHER);
    }

    @Test
    void validate_freeShopCheck_pricesNotFree_passesThrough() {
        Player client = player("Notch", UUID.randomUUID());
        when(config.isAllowFreeShops()).thenReturn(false);
        when(config.isAllowPartialTransactions()).thenReturn(true);
        try (MockedStatic<SignService> ss = mockStatic(SignService.class)) {
            ss.when(() -> SignService.getPrice(any(Sign.class))).thenReturn("b5:s3"); // not free
            ss.when(() -> SignService.getItem(any(Sign.class))).thenReturn("STONE");
            when(adminBypass.has(any(), any())).thenReturn(true);
            when(inventoryService.fits(any(ItemStack[].class), any())).thenReturn(true);
            PendingTransaction ctx = pending(BUY, true, sign(location(mock(World.class))), client, owner(),
                    mock(Inventory.class), new ItemStack[]{item(Material.STONE, 1)}, new BigDecimal("5"));
            service.validate(ctx);
            assertThat(ctx.isCancelled()).isFalse();
        }
    }

    @Test
    void validate_buy_unlimitedOwner_skipsWholeStockCheck() {
        Player client = player("Notch", UUID.randomUUID());
        setupWholeTradeConfig();
        economy.hasFunds = (u, a) -> true;
        when(adminBypass.has(any(), any())).thenReturn(true);
        when(inventoryService.fits(any(ItemStack[].class), any())).thenReturn(true);
        try (MockedStatic<SignService> ss = mockStatic(SignService.class)) {
            ss.when(() -> SignService.getItem(any(Sign.class))).thenReturn("STONE");
            Sign s = sign(location(mock(World.class)));
            when(signService.isAdminShop(s)).thenReturn(true);
            PendingTransaction ctx = pending(BUY, true, s, client, owner(), null,
                    new ItemStack[]{item(Material.STONE, 1)}, new BigDecimal("5"));
            service.validate(ctx);
            assertThat(ctx.isCancelled()).isFalse();
        }
    }

    @Test
    void validate_buy_permissionGrantedPerMaterialNode() {
        Player client = player("Notch", UUID.randomUUID());
        setupWholeTradeConfig();
        economy.hasFunds = (u, a) -> true;
        when(inventoryService.hasItems(any(), any())).thenReturn(true);
        when(inventoryService.fits(any(ItemStack[].class), any())).thenReturn(true);
        when(adminBypass.has(eq(client), eq(io.paradaux.chestshop.utils.Permissions.BUY))).thenReturn(false);
        when(adminBypass.has(eq(client), eq(io.paradaux.chestshop.utils.Permissions.BUY_ID + "stone"))).thenReturn(true);
        try (MockedStatic<SignService> ss = mockStatic(SignService.class)) {
            ss.when(() -> SignService.getItem(any(Sign.class))).thenReturn("STONE");
            PendingTransaction ctx = wholeTradeCtx(BUY, client);
            service.validate(ctx);
            assertThat(ctx.isCancelled()).isFalse();
        }
    }

    @Test
    void validate_hashItemLine_notSetFalse_proceedsToLoop() {
        Player client = player("Notch", UUID.randomUUID());
        setupWholeTradeConfig();
        economy.hasFunds = (u, a) -> true;
        when(inventoryService.hasItems(any(), any())).thenReturn(true);
        when(inventoryService.fits(any(ItemStack[].class), any())).thenReturn(true);
        when(adminBypass.has(any(), any())).thenReturn(true);
        try (MockedStatic<SignService> ss = mockStatic(SignService.class)) {
            ss.when(() -> SignService.getItem(any(Sign.class))).thenReturn("#7"); // '#' present but no set-false override
            PendingTransaction ctx = wholeTradeCtx(BUY, client);
            service.validate(ctx);
            assertThat(ctx.isCancelled()).isFalse();
        }
    }

    @Test
    void sendError_notEnoughSpaceInChest_notifiesWhenConfigFlagOn() {
        try (MockedStatic<Bukkit> bk = mockStatic(Bukkit.class)) {
            World world = mock(World.class);
            when(world.getName()).thenReturn("world");
            UUID ownerId = UUID.randomUUID();
            Account owner = new Account("Alice", "Alice", ownerId);
            Player ownerPlayer = player("Alice", ownerId);
            bk.when(() -> Bukkit.getPlayer(ownerId)).thenReturn(ownerPlayer);
            when(accounts.isIgnoring(ownerId)).thenReturn(true); // ignoring, but the config flag forces the message
            when(config.isShowMessageFullShop()).thenReturn(true);
            when(config.isCstoggleTogglesFullShop()).thenReturn(false);
            Player client = player("Notch", UUID.randomUUID());
            when(config.isAllowFreeShops()).thenReturn(true);
            when(config.isAllowPartialTransactions()).thenReturn(true);
            doAnswer(inv -> { ((PendingTransaction) inv.getArgument(0)).setCancelled(TransactionOutcome.NOT_ENOUGH_SPACE_IN_CHEST); return null; })
                    .when(partialFill).adjustBuy(any());
            PendingTransaction ctx = pending(BUY, false, sign(location(world)), client, owner, mock(Inventory.class),
                    new ItemStack[]{item(Material.STONE, 1)}, new BigDecimal("5"));
            service.validate(ctx);
            verify(ownerPlayer).sendMessage(any(net.kyori.adventure.text.Component.class));
        }
    }

    @Test
    void sendError_notEnoughStockInChest_notifiesWhenConfigFlagOn() {
        try (MockedStatic<Bukkit> bk = mockStatic(Bukkit.class)) {
            World world = mock(World.class);
            when(world.getName()).thenReturn("world");
            UUID ownerId = UUID.randomUUID();
            Account owner = new Account("Alice", "Alice", ownerId);
            Player ownerPlayer = player("Alice", ownerId);
            bk.when(() -> Bukkit.getPlayer(ownerId)).thenReturn(ownerPlayer);
            when(accounts.isIgnoring(ownerId)).thenReturn(true);
            when(config.isShowMessageOutOfStock()).thenReturn(true);
            when(config.isCstoggleTogglesOutOfStock()).thenReturn(false);
            Player client = player("Notch", UUID.randomUUID());
            when(config.isAllowFreeShops()).thenReturn(true);
            when(config.isAllowPartialTransactions()).thenReturn(true);
            doAnswer(inv -> { ((PendingTransaction) inv.getArgument(0)).setCancelled(TransactionOutcome.NOT_ENOUGH_STOCK_IN_CHEST); return null; })
                    .when(partialFill).adjustBuy(any());
            PendingTransaction ctx = pending(BUY, false, sign(location(world)), client, owner, mock(Inventory.class),
                    new ItemStack[]{item(Material.STONE, 1)}, new BigDecimal("5"));
            service.validate(ctx);
            verify(ownerPlayer).sendMessage(any(net.kyori.adventure.text.Component.class));
        }
    }

    @Test
    void deleteEmptyShop_removesShop_worldInRemoveList_emptyChest() {
        try (MockedStatic<ChestShop> cs = mockStatic(ChestShop.class);
             MockedStatic<Bukkit> bk = mockStatic(Bukkit.class)) {
            World world = mock(World.class);
            when(world.getName()).thenReturn("world");
            Sign s = sign(location(world));
            when(s.getType()).thenReturn(Material.OAK_SIGN);
            Block signBlock = mock(Block.class);
            when(s.getBlock()).thenReturn(signBlock);
            Inventory ownerInv = mock(Inventory.class);
            when(ownerInv.getStorageContents()).thenReturn(new ItemStack[]{null});
            Account owner = new Account("Alice", "Alice", UUID.randomUUID());
            Transaction event = deletableBuy(cs, s, ownerInv, owner);
            when(signService.isAdminShop(s)).thenReturn(false);
            when(config.isRemoveEmptyShops()).thenReturn(true);
            when(config.isAllowPartialTransactions()).thenReturn(true);
            when(ownerInv.containsAtLeast(any(), eq(1))).thenReturn(false);
            when(config.getRemoveEmptyWorlds()).thenReturn(java.util.Collections.singleton("world")); // world listed → contains true
            when(config.isRemoveEmptyChests()).thenReturn(true);
            Container container = mock(Container.class);
            when(container.getBlock()).thenReturn(mock(Block.class));
            when(shopBlockService.findConnectedContainer(s)).thenReturn(container);

            service.process(event);

            verify(shops).onDestroyed(any());
        }
    }

    @Test
    void deleteEmptyShop_removesShop_emptyChest_butNoConnectedContainer() {
        // covers the `container != null` false arc when clearing an empty chest.
        try (MockedStatic<ChestShop> cs = mockStatic(ChestShop.class);
             MockedStatic<Bukkit> bk = mockStatic(Bukkit.class)) {
            World world = mock(World.class);
            when(world.getName()).thenReturn("world");
            Sign s = sign(location(world));
            when(s.getType()).thenReturn(Material.OAK_SIGN);
            when(s.getBlock()).thenReturn(mock(Block.class));
            Inventory ownerInv = mock(Inventory.class);
            when(ownerInv.getStorageContents()).thenReturn(new ItemStack[]{null});
            Account owner = new Account("Alice", "Alice", UUID.randomUUID());
            Transaction event = deletableBuy(cs, s, ownerInv, owner);
            when(signService.isAdminShop(s)).thenReturn(false);
            when(config.isRemoveEmptyShops()).thenReturn(true);
            when(config.isAllowPartialTransactions()).thenReturn(true);
            when(ownerInv.containsAtLeast(any(), eq(1))).thenReturn(false);
            when(config.getRemoveEmptyWorlds()).thenReturn(java.util.Collections.emptySet());
            when(config.isRemoveEmptyChests()).thenReturn(true);
            when(shopBlockService.findConnectedContainer(s)).thenReturn(null); // no container

            service.process(event);

            verify(shops).onDestroyed(any());
        }
    }

    @Test
    void process_sellTrade_notifiesBothParties() {
        try (MockedStatic<ChestShop> cs = mockStatic(ChestShop.class);
             MockedStatic<Bukkit> bk = mockStatic(Bukkit.class)) {
            World world = mock(World.class);
            when(world.getName()).thenReturn("world");
            UUID ownerId = UUID.randomUUID();
            Player client = player("Notch", UUID.randomUUID());
            Account owner = new Account("Alice", "Alice", ownerId);
            Player ownerPlayer = player("Alice", ownerId);
            Sign s = sign(location(world));
            Transaction event = txn(SELL, false, s, client, owner, mock(Inventory.class), mock(Inventory.class),
                    new ItemStack[]{item(Material.STONE, 1)}, new BigDecimal("5.00"));
            when(goodsTransfer.transfer(any(), any(), any())).thenReturn(true);
            economy.settleResult = true;
            when(signService.isAdminShop(s)).thenReturn(false);
            Map<ItemStack, Integer> counts = new LinkedHashMap<>();
            counts.put(item(Material.STONE, 1), 1);
            when(inventoryService.getItemCounts(any())).thenReturn(counts);
            cs.when(() -> ChestShop.runInAsyncThread(any())).thenAnswer(inv -> null);
            when(config.isShowTransactionInformationClient()).thenReturn(true);
            when(config.isShowTransactionInformationOwner()).thenReturn(true);
            when(accounts.isIgnoring(ownerId)).thenReturn(false);
            bk.when(() -> Bukkit.getPlayer(ownerId)).thenReturn(ownerPlayer);

            service.process(event);

            verify(client).sendMessage(any(net.kyori.adventure.text.Component.class));
            verify(ownerPlayer).sendMessage(any(net.kyori.adventure.text.Component.class));
        }
    }

    @Test
    void prepare_adminShopWithContainer_notForced_isNotUnlimited() {
        try (MockedStatic<SignService> ss = mockStatic(SignService.class)) {
            Sign s = prepSign();
            ss.when(() -> SignService.getOwner(s)).thenReturn("Server");
            ss.when(() -> SignService.getPrice(s)).thenReturn("b5:s3");
            ss.when(() -> SignService.getItem(s)).thenReturn("STONE");
            ss.when(() -> SignService.getQuantity(s)).thenReturn(1);
            Account acc = new Account("Server", "Server", UUID.randomUUID());
            when(accounts.resolveAccount("Server")).thenReturn(acc);
            when(signService.isAdminShop(s)).thenReturn(true);
            ItemStack parsed = item(Material.STONE, 1);
            when(items.parse("STONE")).thenReturn(parsed);
            Container container = mock(Container.class);
            when(container.getInventory()).thenReturn(mock(Inventory.class));
            when(shopBlockService.findConnectedContainer(s)).thenReturn(container);
            when(config.isForceUnlimitedAdminShop()).thenReturn(false);
            when(inventoryService.getItemsStacked(parsed)).thenReturn(new ItemStack[]{parsed});

            PendingTransaction ctx = service.prepare(s, player("Notch", UUID.randomUUID()), Action.RIGHT_CLICK_BLOCK);

            assertThat(ctx.isUnlimitedOwner()).isFalse();
        }
    }

    @Test
    void prepare_shiftSellsInStacks_sellDirection_usesPlayerInventory() {
        try (MockedStatic<SignService> ss = mockStatic(SignService.class)) {
            Sign s = prepSign();
            ss.when(() -> SignService.getOwner(s)).thenReturn("Alice");
            ss.when(() -> SignService.getPrice(s)).thenReturn("b5:s3");
            ss.when(() -> SignService.getItem(s)).thenReturn("STONE");
            ss.when(() -> SignService.getQuantity(s)).thenReturn(1);
            Account acc = new Account("Alice", "Alice", UUID.randomUUID());
            when(accounts.resolveAccount("Alice")).thenReturn(acc);
            when(signService.isAdminShop(s)).thenReturn(false);
            economy.hasAccount = true;
            ItemStack parsed = item(Material.STONE, 1);
            when(items.parse("STONE")).thenReturn(parsed);
            Container container = mock(Container.class);
            when(container.getInventory()).thenReturn(mock(Inventory.class));
            when(shopBlockService.findConnectedContainer(s)).thenReturn(container);
            Player p = player("Notch", UUID.randomUUID());
            PlayerInventory pInv = (PlayerInventory) p.getInventory();
            when(p.isSneaking()).thenReturn(true);
            when(config.isShiftSellsInStacks()).thenReturn(true);
            when(config.getShiftAllows()).thenReturn("ALL");
            when(inventoryService.getMaxStackSize(parsed)).thenReturn(64);
            // SELL direction → getStackAmount checks the player inventory; not full → getAmount
            when(pInv.containsAtLeast(parsed, 64)).thenReturn(false);
            when(inventoryService.getAmount(parsed, pInv)).thenReturn(5);
            when(inventoryService.getItemsStacked(parsed)).thenReturn(new ItemStack[]{parsed});

            PendingTransaction ctx = service.prepare(s, p, Action.LEFT_CLICK_BLOCK); // SELL

            assertThat(ctx.getTransactionType()).isEqualTo(SELL);
            assertThat(ctx.getExactPrice()).isEqualByComparingTo("15"); // 3 * 5
        }
    }

    @Test
    void prepare_shiftSellsEverything_zeroAmount_leavesQuantityUnchanged() {
        try (MockedStatic<SignService> ss = mockStatic(SignService.class)) {
            Sign s = prepSign();
            ss.when(() -> SignService.getOwner(s)).thenReturn("Alice");
            ss.when(() -> SignService.getPrice(s)).thenReturn("b5:s3");
            ss.when(() -> SignService.getItem(s)).thenReturn("STONE");
            ss.when(() -> SignService.getQuantity(s)).thenReturn(1);
            Account acc = new Account("Alice", "Alice", UUID.randomUUID());
            when(accounts.resolveAccount("Alice")).thenReturn(acc);
            when(signService.isAdminShop(s)).thenReturn(false);
            economy.hasAccount = true;
            ItemStack parsed = item(Material.STONE, 1);
            when(items.parse("STONE")).thenReturn(parsed);
            Container container = mock(Container.class);
            when(container.getInventory()).thenReturn(mock(Inventory.class));
            when(shopBlockService.findConnectedContainer(s)).thenReturn(container);
            Player p = player("Notch", UUID.randomUUID());
            PlayerInventory pInv = (PlayerInventory) p.getInventory();
            when(p.isSneaking()).thenReturn(true);
            when(config.isShiftSellsEverything()).thenReturn(true);
            when(config.getShiftAllows()).thenReturn("ALL");
            when(inventoryService.getAmount(parsed, pInv)).thenReturn(0); // nothing to sell → newAmount 0 → unchanged

            PendingTransaction ctx = service.prepare(s, p, Action.LEFT_CLICK_BLOCK);

            assertThat(ctx.getExactPrice()).isEqualByComparingTo("3"); // unchanged
        }
    }

    @Test
    void prepare_shiftSellsEverything_buyAdminShop_skipsOwnerBranch() {
        try (MockedStatic<SignService> ss = mockStatic(SignService.class)) {
            Sign s = prepSign();
            ss.when(() -> SignService.getOwner(s)).thenReturn("Server");
            ss.when(() -> SignService.getPrice(s)).thenReturn("b5:s3");
            ss.when(() -> SignService.getItem(s)).thenReturn("STONE");
            ss.when(() -> SignService.getQuantity(s)).thenReturn(1);
            Account acc = new Account("Server", "Server", UUID.randomUUID());
            when(accounts.resolveAccount("Server")).thenReturn(acc);
            when(signService.isAdminShop(s)).thenReturn(true);
            ItemStack parsed = item(Material.STONE, 1);
            when(items.parse("STONE")).thenReturn(parsed);
            when(shopBlockService.findConnectedContainer(s)).thenReturn(null); // admin, no container
            Player p = player("Notch", UUID.randomUUID());
            when(p.isSneaking()).thenReturn(true);
            when(config.isShiftSellsEverything()).thenReturn(true);
            when(config.getShiftAllows()).thenReturn("ALL");
            when(inventoryService.getItemsStacked(parsed)).thenReturn(new ItemStack[]{parsed});

            // BUY + admin/no-owner-inv → the else-if owner branch is skipped, price unchanged.
            PendingTransaction ctx = service.prepare(s, p, Action.RIGHT_CLICK_BLOCK);

            assertThat(ctx.getExactPrice()).isEqualByComparingTo("5");
        }
    }

    @Test
    void prepare_notSneaking_skipsShift() {
        try (MockedStatic<SignService> ss = mockStatic(SignService.class)) {
            Sign s = prepSign();
            ss.when(() -> SignService.getOwner(s)).thenReturn("Alice");
            ss.when(() -> SignService.getPrice(s)).thenReturn("b5:s3");
            ss.when(() -> SignService.getItem(s)).thenReturn("STONE");
            ss.when(() -> SignService.getQuantity(s)).thenReturn(1);
            Account acc = new Account("Alice", "Alice", UUID.randomUUID());
            when(accounts.resolveAccount("Alice")).thenReturn(acc);
            when(signService.isAdminShop(s)).thenReturn(false);
            economy.hasAccount = true;
            ItemStack parsed = item(Material.STONE, 1);
            when(items.parse("STONE")).thenReturn(parsed);
            Container container = mock(Container.class);
            when(container.getInventory()).thenReturn(mock(Inventory.class));
            when(shopBlockService.findConnectedContainer(s)).thenReturn(container);
            Player p = player("Notch", UUID.randomUUID());
            when(p.isSneaking()).thenReturn(false); // not sneaking → both shift blocks skipped
            when(config.isShiftSellsInStacks()).thenReturn(true);
            when(config.isShiftSellsEverything()).thenReturn(true);
            when(inventoryService.getItemsStacked(parsed)).thenReturn(new ItemStack[]{parsed});

            PendingTransaction ctx = service.prepare(s, p, Action.RIGHT_CLICK_BLOCK);

            assertThat(ctx.getExactPrice()).isEqualByComparingTo("5");
        }
    }

    // ── last branch fills ──

    private void prepBasics(MockedStatic<SignService> ss, Sign s, String owner, String price, boolean admin) {
        ss.when(() -> SignService.getOwner(s)).thenReturn(owner);
        ss.when(() -> SignService.getPrice(s)).thenReturn(price);
        ss.when(() -> SignService.getItem(s)).thenReturn("STONE");
        ss.when(() -> SignService.getQuantity(s)).thenReturn(1);
        when(accounts.resolveAccount(owner)).thenReturn(new Account(owner, owner, UUID.randomUUID()));
        when(signService.isAdminShop(s)).thenReturn(admin);
        economy.hasAccount = true;
    }

    @Test
    void prepare_shiftEnabled_butNoPriceForDirection_skipsShift() {
        try (MockedStatic<SignService> ss = mockStatic(SignService.class)) {
            Sign s = prepSign();
            prepBasics(ss, s, "Alice", "s3", false); // buy price absent → getExactBuyPrice == NO_PRICE
            ItemStack parsed = item(Material.STONE, 1);
            when(items.parse("STONE")).thenReturn(parsed);
            Container container = mock(Container.class);
            when(container.getInventory()).thenReturn(mock(Inventory.class));
            when(shopBlockService.findConnectedContainer(s)).thenReturn(container);
            Player p = player("Notch", UUID.randomUUID());
            when(p.isSneaking()).thenReturn(true);
            when(config.isShiftSellsInStacks()).thenReturn(true);
            when(config.isShiftSellsEverything()).thenReturn(true);
            when(inventoryService.getItemsStacked(parsed)).thenReturn(new ItemStack[]{parsed});

            PendingTransaction ctx = service.prepare(s, p, Action.RIGHT_CLICK_BLOCK); // BUY with no buy price

            assertThat(ctx.getExactPrice()).isEqualByComparingTo("-1"); // NO_PRICE, shift skipped
        }
    }

    @Test
    void prepare_shiftEverything_buy_nonAdminNullChest_skipsOwnerBranch() {
        try (MockedStatic<SignService> ss = mockStatic(SignService.class)) {
            Sign s = prepSign();
            prepBasics(ss, s, "Alice", "b5:s3", false);
            ItemStack parsed = item(Material.STONE, 1);
            when(items.parse("STONE")).thenReturn(parsed);
            when(shopBlockService.findConnectedContainer(s)).thenReturn(null); // non-admin but no chest → ownerInv null
            Player p = player("Notch", UUID.randomUUID());
            when(p.isSneaking()).thenReturn(true);
            when(config.isShiftSellsEverything()).thenReturn(true);
            when(config.getShiftAllows()).thenReturn("ALL");
            when(inventoryService.getItemsStacked(parsed)).thenReturn(new ItemStack[]{parsed});

            PendingTransaction ctx = service.prepare(s, p, Action.RIGHT_CLICK_BLOCK);

            assertThat(ctx.getExactPrice()).isEqualByComparingTo("5"); // owner branch skipped (ownerInv null)
        }
    }

    @Test
    void prepare_shiftEverything_buy_ownerChestEmpty_leavesUnchanged() {
        try (MockedStatic<SignService> ss = mockStatic(SignService.class)) {
            Sign s = prepSign();
            prepBasics(ss, s, "Alice", "b5:s3", false);
            ItemStack parsed = item(Material.STONE, 1);
            when(items.parse("STONE")).thenReturn(parsed);
            Container container = mock(Container.class);
            Inventory ownerInv = mock(Inventory.class);
            when(container.getInventory()).thenReturn(ownerInv);
            when(shopBlockService.findConnectedContainer(s)).thenReturn(container);
            Player p = player("Notch", UUID.randomUUID());
            when(p.isSneaking()).thenReturn(true);
            when(config.isShiftSellsEverything()).thenReturn(true);
            when(config.getShiftAllows()).thenReturn("ALL");
            when(inventoryService.getAmount(parsed, ownerInv)).thenReturn(0); // empty chest → newAmount 0 → unchanged
            when(inventoryService.getItemsStacked(parsed)).thenReturn(new ItemStack[]{parsed});

            PendingTransaction ctx = service.prepare(s, p, Action.RIGHT_CLICK_BLOCK);

            assertThat(ctx.getExactPrice()).isEqualByComparingTo("5");
        }
    }

    @Test
    void prepare_getStackAmount_withReverseButtons() {
        try (MockedStatic<SignService> ss = mockStatic(SignService.class)) {
            Sign s = prepSign();
            prepBasics(ss, s, "Alice", "b5:s3", false);
            ItemStack parsed = item(Material.STONE, 1);
            when(items.parse("STONE")).thenReturn(parsed);
            Container container = mock(Container.class);
            Inventory ownerInv = mock(Inventory.class);
            when(container.getInventory()).thenReturn(ownerInv);
            when(shopBlockService.findConnectedContainer(s)).thenReturn(container);
            Player p = player("Notch", UUID.randomUUID());
            when(p.isSneaking()).thenReturn(true);
            when(config.isReverseButtons()).thenReturn(true); // buy is LEFT
            when(config.isShiftSellsInStacks()).thenReturn(true);
            when(config.getShiftAllows()).thenReturn("ALL");
            when(inventoryService.getMaxStackSize(parsed)).thenReturn(64);
            when(ownerInv.containsAtLeast(parsed, 64)).thenReturn(false);
            when(inventoryService.getAmount(parsed, ownerInv)).thenReturn(3);
            when(inventoryService.getItemsStacked(parsed)).thenReturn(new ItemStack[]{parsed});

            PendingTransaction ctx = service.prepare(s, p, Action.LEFT_CLICK_BLOCK); // BUY (reversed)

            assertThat(ctx.getTransactionType()).isEqualTo(BUY);
            assertThat(ctx.getExactPrice()).isEqualByComparingTo("15"); // 5 * 3
        }
    }

    @Test
    void validate_creativeIgnoredButPlayerSurvival_passes() {
        Player client = player("Notch", UUID.randomUUID());
        when(client.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(config.isIgnoreCreativeMode()).thenReturn(true); // enabled, but the player isn't creative
        setupWholeTradeConfig();
        economy.hasFunds = (u, a) -> true;
        when(inventoryService.hasItems(any(), any())).thenReturn(true);
        when(inventoryService.fits(any(ItemStack[].class), any())).thenReturn(true);
        when(adminBypass.has(any(), any())).thenReturn(true);
        try (MockedStatic<SignService> ss = mockStatic(SignService.class)) {
            ss.when(() -> SignService.getItem(any(Sign.class))).thenReturn("STONE");
            PendingTransaction ctx = wholeTradeCtx(BUY, client);
            service.validate(ctx);
            assertThat(ctx.isCancelled()).isFalse();
        }
    }

    @Test
    void validate_sellFreeShop_isFlagged() {
        Player client = player("Notch", UUID.randomUUID());
        when(config.isAllowFreeShops()).thenReturn(false);
        Sign s = sign(location(mock(World.class)));
        when(s.getBlock()).thenReturn(mock(Block.class));
        try (MockedStatic<SignService> ss = mockStatic(SignService.class)) {
            ss.when(() -> SignService.getPrice(s)).thenReturn("b5:s0"); // buy 5 (not free), sell 0 (free)
            PendingTransaction ctx = pending(BUY, false, s, client, owner(), mock(Inventory.class),
                    new ItemStack[]{item(Material.STONE, 1)}, new BigDecimal("5"));
            service.validate(ctx);
            assertThat(ctx.isRejectedAsFreeShop()).isTrue();
        }
    }

    @Test
    void validate_buy_permissionDeniedWhenBasePermissionSetFalse() {
        Player client = player("Notch", UUID.randomUUID());
        setupWholeTradeConfig();
        economy.hasFunds = (u, a) -> true;
        when(inventoryService.hasItems(any(), any())).thenReturn(true);
        when(adminBypass.has(eq(client), eq(io.paradaux.chestshop.utils.Permissions.BUY))).thenReturn(true);
        when(adminBypass.has(eq(client), eq(io.paradaux.chestshop.utils.Permissions.BUY_ID + "stone"))).thenReturn(false);
        try (MockedStatic<SignService> ss = mockStatic(SignService.class);
             MockedStatic<io.paradaux.chestshop.utils.Permissions> perms =
                     mockStatic(io.paradaux.chestshop.utils.Permissions.class)) {
            ss.when(() -> SignService.getItem(any(Sign.class))).thenReturn("STONE");
            // base BUY held, but the per-material node is explicitly set-false → the &&-clause fails,
            // and the per-material grant is also absent → denied.
            perms.when(() -> io.paradaux.chestshop.utils.Permissions.hasPermissionSetFalse(
                    eq(client), eq(io.paradaux.chestshop.utils.Permissions.BUY_ID + "stone"))).thenReturn(true);
            PendingTransaction ctx = wholeTradeCtx(BUY, client);
            service.validate(ctx);
            assertThat(ctx.getTransactionOutcome()).isEqualTo(TransactionOutcome.CLIENT_DOES_NOT_HAVE_PERMISSION);
        }
    }

    @Test
    void validate_sell_permissionDeniedByHashNode() {
        Player client = player("Notch", UUID.randomUUID());
        setupWholeTradeConfig();
        economy.hasFunds = (u, a) -> true;
        when(inventoryService.hasItems(any(), any())).thenReturn(true);
        try (MockedStatic<SignService> ss = mockStatic(SignService.class);
             MockedStatic<io.paradaux.chestshop.utils.Permissions> perms =
                     mockStatic(io.paradaux.chestshop.utils.Permissions.class)) {
            ss.when(() -> SignService.getItem(any(Sign.class))).thenReturn("#9");
            perms.when(() -> io.paradaux.chestshop.utils.Permissions.hasPermissionSetFalse(eq(client), any()))
                    .thenReturn(true); // SELL_ID + "#9" set-false → denied
            PendingTransaction ctx = wholeTradeCtx(SELL, client);
            service.validate(ctx);
            assertThat(ctx.getTransactionOutcome()).isEqualTo(TransactionOutcome.CLIENT_DOES_NOT_HAVE_PERMISSION);
        }
    }

    @Test
    void validate_sell_permissionDeniedWhenBaseSetFalseAndNoMaterialNode() {
        Player client = player("Notch", UUID.randomUUID());
        setupWholeTradeConfig();
        economy.hasFunds = (u, a) -> true;
        when(inventoryService.hasItems(any(), any())).thenReturn(true);
        when(inventoryService.fits(any(ItemStack[].class), any())).thenReturn(true);
        when(adminBypass.has(eq(client), eq(io.paradaux.chestshop.utils.Permissions.SELL))).thenReturn(true);
        when(adminBypass.has(eq(client), eq(io.paradaux.chestshop.utils.Permissions.SELL_ID + "stone"))).thenReturn(false);
        try (MockedStatic<SignService> ss = mockStatic(SignService.class);
             MockedStatic<io.paradaux.chestshop.utils.Permissions> perms =
                     mockStatic(io.paradaux.chestshop.utils.Permissions.class)) {
            ss.when(() -> SignService.getItem(any(Sign.class))).thenReturn("STONE");
            perms.when(() -> io.paradaux.chestshop.utils.Permissions.hasPermissionSetFalse(
                    eq(client), eq(io.paradaux.chestshop.utils.Permissions.SELL_ID + "stone"))).thenReturn(true);
            PendingTransaction ctx = wholeTradeCtx(SELL, client);
            service.validate(ctx);
            assertThat(ctx.getTransactionOutcome()).isEqualTo(TransactionOutcome.CLIENT_DOES_NOT_HAVE_PERMISSION);
        }
    }

    @Test
    void sendError_ownerNotification_resendsAfterCooldownExpires() throws InterruptedException {
        try (MockedStatic<Bukkit> bk = mockStatic(Bukkit.class)) {
            World world = mock(World.class);
            when(world.getName()).thenReturn("world");
            UUID ownerId = UUID.randomUUID();
            Account owner = new Account("Alice", "Alice", ownerId);
            Player ownerPlayer = player("Alice", ownerId);
            bk.when(() -> Bukkit.getPlayer(ownerId)).thenReturn(ownerPlayer);
            when(accounts.isIgnoring(ownerId)).thenReturn(false);
            when(config.getNotificationMessageCooldown()).thenReturn(1L); // 1-second window
            Player client = player("Notch", UUID.randomUUID());
            when(config.isAllowFreeShops()).thenReturn(true);
            when(config.isAllowPartialTransactions()).thenReturn(true);
            doAnswer(inv -> { ((PendingTransaction) inv.getArgument(0)).setCancelled(TransactionOutcome.NOT_ENOUGH_SPACE_IN_CHEST); return null; })
                    .when(partialFill).adjustBuy(any());

            service.validate(pending(BUY, false, sign(location(world)), client, owner, mock(Inventory.class),
                    new ItemStack[]{item(Material.STONE, 1)}, new BigDecimal("5")));
            Thread.sleep(1100); // let the cooldown window lapse
            service.validate(pending(BUY, false, sign(location(world)), client, owner, mock(Inventory.class),
                    new ItemStack[]{item(Material.STONE, 1)}, new BigDecimal("5")));

            // Cooldown expired between the two → both notifications delivered.
            verify(ownerPlayer, org.mockito.Mockito.times(2)).sendMessage(any(net.kyori.adventure.text.Component.class));
        }
    }

    @Test
    void prepare_shiftInStacks_zeroStackAmount_leavesUnchanged() {
        try (MockedStatic<SignService> ss = mockStatic(SignService.class)) {
            Sign s = prepSign();
            prepBasics(ss, s, "Alice", "b5:s3", false);
            ItemStack parsed = item(Material.STONE, 1);
            when(items.parse("STONE")).thenReturn(parsed);
            Container container = mock(Container.class);
            Inventory ownerInv = mock(Inventory.class);
            when(container.getInventory()).thenReturn(ownerInv);
            when(shopBlockService.findConnectedContainer(s)).thenReturn(container);
            Player p = player("Notch", UUID.randomUUID());
            when(p.isSneaking()).thenReturn(true);
            when(config.isShiftSellsInStacks()).thenReturn(true);
            when(config.getShiftAllows()).thenReturn("ALL");
            when(inventoryService.getMaxStackSize(parsed)).thenReturn(64);
            when(ownerInv.containsAtLeast(parsed, 64)).thenReturn(false);
            when(inventoryService.getAmount(parsed, ownerInv)).thenReturn(0); // getStackAmount → 0 → newAmount not applied
            when(inventoryService.getItemsStacked(parsed)).thenReturn(new ItemStack[]{parsed});

            PendingTransaction ctx = service.prepare(s, p, Action.RIGHT_CLICK_BLOCK);

            assertThat(ctx.getExactPrice()).isEqualByComparingTo("5"); // unchanged
        }
    }

    @Test
    void prepare_shiftEverything_notAllowedForDirection_skips() {
        try (MockedStatic<SignService> ss = mockStatic(SignService.class)) {
            Sign s = prepSign();
            prepBasics(ss, s, "Alice", "b5:s3", false);
            ItemStack parsed = item(Material.STONE, 1);
            when(items.parse("STONE")).thenReturn(parsed);
            Container container = mock(Container.class);
            when(container.getInventory()).thenReturn(mock(Inventory.class));
            when(shopBlockService.findConnectedContainer(s)).thenReturn(container);
            Player p = player("Notch", UUID.randomUUID());
            when(p.isSneaking()).thenReturn(true);
            when(config.isShiftSellsEverything()).thenReturn(true);
            when(config.getShiftAllows()).thenReturn("BUY"); // SELL click but only BUY shift allowed → isAllowedForShift false
            when(inventoryService.getItemsStacked(parsed)).thenReturn(new ItemStack[]{parsed});

            PendingTransaction ctx = service.prepare(s, p, Action.LEFT_CLICK_BLOCK); // SELL

            assertThat(ctx.getExactPrice()).isEqualByComparingTo("3"); // unchanged
        }
    }

    @Test
    void sendError_fullShop_suppressedWhenCstoggleTogglesAndIgnoring() {
        Player client = player("Notch", UUID.randomUUID());
        Account owner = owner();
        when(accounts.isIgnoring(owner.getUuid())).thenReturn(true);
        when(config.isShowMessageFullShop()).thenReturn(true);
        when(config.isCstoggleTogglesFullShop()).thenReturn(true); // cstoggle on → the config term is false
        PendingTransaction ctx = injectOutcome(TransactionOutcome.NOT_ENOUGH_SPACE_IN_CHEST, client, owner, mock(Inventory.class));
        service.validate(ctx);
        verify(message).send(client, "chestshop.NOT_ENOUGH_SPACE_IN_CHEST");
    }

    @Test
    void sendError_outOfStock_suppressedWhenCstoggleTogglesAndIgnoring() {
        Player client = player("Notch", UUID.randomUUID());
        Account owner = owner();
        when(accounts.isIgnoring(owner.getUuid())).thenReturn(true);
        when(config.isShowMessageOutOfStock()).thenReturn(true);
        when(config.isCstoggleTogglesOutOfStock()).thenReturn(true);
        PendingTransaction ctx = injectOutcome(TransactionOutcome.NOT_ENOUGH_STOCK_IN_CHEST, client, owner, mock(Inventory.class));
        service.validate(ctx);
        verify(message).send(client, "chestshop.NOT_ENOUGH_STOCK");
    }

    @Test
    void removeFlaggedFreeShop_guardsAgainstNullSign() {
        // Defensive guard: a free-shop rejection flag with no sign is a no-op (the destructive
        // removal needs a block to break). rejectedAsFreeShop is set via its public setter here;
        // an invalid client name cancels early so removeFlaggedFreeShop is the only step that runs.
        Player client = player("bad name!", UUID.randomUUID());
        when(signService.isAdminShop("bad name!")).thenReturn(false);
        PendingTransaction ctx = pending(BUY, false, null, client, owner(), mock(Inventory.class),
                new ItemStack[]{item(Material.STONE, 1)}, new BigDecimal("5"));
        ctx.setRejectedAsFreeShop(true);

        service.validate(ctx);

        verify(signBreak, never()).sendShopDestroyed(any(), any());
    }

    @Test
    void sendTradeMessage_usesPlaceholderForUnloadedWorld() throws Exception {
        // sendTradeMessage's world-null guard (ADT-140) is unreachable through process() because
        // logTransaction dereferences the world first; exercise it directly.
        Player recipient = player("Alice", UUID.randomUUID());
        Sign s = sign(location(null)); // unloaded world
        Account owner = new Account("Alice", "Alice", UUID.randomUUID());
        Transaction event = txn(BUY, false, s, player("Notch", UUID.randomUUID()), owner,
                mock(Inventory.class), mock(Inventory.class), new ItemStack[]{item(Material.STONE, 1)}, new BigDecimal("5"));

        var m = TransactionServiceImpl.class.getDeclaredMethod(
                "sendTradeMessage", Player.class, String.class, Transaction.class, String[].class);
        m.setAccessible(true);
        m.invoke(service, recipient, "chestshop.YOU_BOUGHT_FROM_SHOP", event, new String[]{"owner", "Alice"});

        verify(recipient).sendMessage(any(net.kyori.adventure.text.Component.class));
    }
    /**
     * Hand-written {@link io.paradaux.chestshop.services.EconomyService} double. It can't be a
     * Mockito mock: the interface's {@code bind(TreasuryApi, int, TaxApi)} forces ByteBuddy to load
     * Treasury API types that are deliberately absent from the test runtime. A concrete empty
     * {@code bind} body avoids that. Declared {@code private} so the JUnit Platform discovery scan
     * (which calls {@code getMethods()} on non-private test-source classes) skips it before it would
     * hit the same missing-type resolution. Behaviour is driven through public fields.
     */
    private static final class FakeEconomy implements io.paradaux.chestshop.services.EconomyService {
        boolean ownerEconomicallyActive = true;
        java.util.function.BiPredicate<java.util.UUID, java.math.BigDecimal> hasFunds = (u, a) -> true;
        java.util.function.Function<java.util.UUID, java.math.BigDecimal> balance = u -> java.math.BigDecimal.ZERO;
        java.util.function.BiPredicate<java.util.UUID, java.math.BigDecimal> canHold = (u, a) -> true;
        boolean hasAccount = true;
        boolean settleResult = true;
        int settleCalls = 0;
        int migrateCalls = 0;
        @Override public void bind(io.paradaux.treasury.api.TreasuryApi treasury, int systemAccountId, io.paradaux.treasury.api.TaxApi taxApi) { /* unused headless */ }
        @Override public String format(java.math.BigDecimal amount) { return amount.toPlainString(); }
        @Override public boolean isOwnerEconomicallyActive(boolean unlimitedOwner) { return ownerEconomicallyActive; }
        @Override public boolean deposit(java.util.UUID target, java.math.BigDecimal amount, org.bukkit.World world) { return true; }
        @Override public boolean withdraw(java.util.UUID target, java.math.BigDecimal amount, org.bukkit.World world) { return true; }
        @Override public boolean hasFunds(java.util.UUID account, java.math.BigDecimal amount) { return hasFunds.test(account, amount); }
        @Override public java.math.BigDecimal getBalance(java.util.UUID account) { return balance.apply(account); }
        @Override public boolean canHold(java.util.UUID account, java.math.BigDecimal amount) { return canHold.test(account, amount); }
        @Override public boolean hasAccount(java.util.UUID account) { return hasAccount; }
        @Override public boolean settle(java.math.BigDecimal amount, org.bukkit.entity.Player initiator, java.util.UUID partner, io.paradaux.chestshop.model.Transaction txn) { settleCalls++; return settleResult; }
        @Override public void migrateLegacyBusinessSign(io.paradaux.chestshop.model.Transaction event) { migrateCalls++; }
    }
}
