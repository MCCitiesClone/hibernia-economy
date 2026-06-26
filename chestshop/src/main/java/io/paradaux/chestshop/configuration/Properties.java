package io.paradaux.chestshop.configuration;

import io.paradaux.chestshop.Security;
import org.bukkit.Material;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Static, plugin-wide view of ChestShop's configuration, kept for the many
 * static {@code @EventHandler} listeners and util classes that read it directly.
 *
 * <p>The framework {@link ChestShopConfiguration} component is the source of
 * truth (loaded by HiberniaFramework's Configurator); these fields are a mirror
 * refreshed from it via {@link #applyFrom(ChestShopConfiguration)} at enable and
 * on reload. The field initialisers are only a pre-load fallback.
 *
 * @author Acrobot
 */
public class Properties {

    public static boolean DEBUG = false;
    public static boolean INCLUDE_SETTINGS_IN_METRICS = true;
    public static int CACHE_SIZE = 1000;
    public static String DEFAULT_LANGUAGE = "en";
    public static boolean USE_CLIENT_LOCALE = true;
    public static boolean STRIP_PRICE_COLORS = false;
    public static Set<Material> SHOP_CONTAINERS = EnumSet.of(Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL);
    public static int SHOP_INTERACTION_INTERVAL = 250;
    public static boolean IGNORE_CREATIVE_MODE = true;
    public static boolean IGNORE_ACCESS_PERMS = true;
    public static boolean REVERSE_BUTTONS = false;
    public static boolean SHIFT_SELLS_IN_STACKS = false;
    public static boolean SHIFT_SELLS_EVERYTHING = false;
    public static String SHIFT_ALLOWS = "ALL";
    public static boolean ALLOW_SIGN_CHEST_OPEN = false;
    public static boolean SIGN_DYING = true;
    public static boolean ALLOW_LEFT_CLICK_DESTROYING = true;
    public static boolean REMOVE_EMPTY_SHOPS = false;
    public static boolean REMOVE_EMPTY_CHESTS = false;
    public static Set<String> REMOVE_EMPTY_WORLDS = new LinkedHashSet<>(Arrays.asList("world1", "world2"));
    public static String ADMIN_SHOP_NAME = "Admin Shop";
    public static boolean FORCE_UNLIMITED_ADMIN_SHOP = false;
    public static String SERVER_ECONOMY_ACCOUNT = "";
    public static UUID SERVER_ECONOMY_ACCOUNT_UUID = new UUID(0, 0);
    public static double TAX_AMOUNT = 0;
    public static double SERVER_TAX_AMOUNT = 0;
    public static BigDecimal SHOP_CREATION_PRICE = BigDecimal.valueOf(0);
    public static BigDecimal SHOP_REFUND_PRICE = BigDecimal.valueOf(0);
    public static int PRICE_PRECISION = 2;
    public static boolean ENSURE_CORRECT_PLAYERID = false;
    public static String VALID_PLAYERNAME_REGEXP = "^\\.?\\w+$";
    public static boolean BLOCK_SHOPS_WITH_SELL_PRICE_HIGHER_THAN_BUY_PRICE = true;
    public static boolean ALLOW_FREE_SHOPS = false;
    public static int MAX_SHOP_AMOUNT = 3456;
    public static boolean ALLOW_MULTIPLE_SHOPS_AT_ONE_BLOCK = false;
    public static boolean ALLOW_PARTIAL_TRANSACTIONS = true;
    public static boolean ALLOW_AUTO_ITEM_FILL = true;
    public static boolean BUNGEECORD_MESSAGES = false;
    public static boolean SHOW_MESSAGE_OUT_OF_STOCK = true;
    public static boolean SHOW_MESSAGE_FULL_SHOP = true;
    public static long NOTIFICATION_MESSAGE_COOLDOWN = 10;
    public static boolean CSTOGGLE_TOGGLES_OUT_OF_STOCK = false;
    public static boolean CSTOGGLE_TOGGLES_FULL_SHOP = false;
    public static boolean SHOW_TRANSACTION_INFORMATION_CLIENT = true;
    public static boolean SHOW_TRANSACTION_INFORMATION_OWNER = true;
    public static boolean LOG_TO_FILE = false;
    public static boolean LOG_TO_CONSOLE = true;
    public static boolean LOG_ALL_SHOP_REMOVALS = true;
    public static boolean STACK_TO_64 = false;
    public static boolean USE_BUILT_IN_PROTECTION = true;
    public static boolean STICK_SIGNS_TO_CHESTS = false;
    public static boolean TURN_OFF_DEFAULT_PROTECTION_WHEN_PROTECTED_EXTERNALLY = false;
    public static boolean TURN_OFF_SIGN_PROTECTION = false;
    public static boolean TURN_OFF_HOPPER_PROTECTION = false;
    public static boolean CHECK_ACCESS_FOR_SHOP_USE = false;
    public static boolean PROTECT_CHEST_WITH_LWC = false;
    public static Security.Type LWC_CHEST_PROTECTION_TYPE = Security.Type.PRIVATE;
    public static boolean PROTECT_SIGN_WITH_LWC = false;
    public static Security.Type LWC_SIGN_PROTECTION_TYPE = Security.Type.PRIVATE;
    public static boolean REMOVE_LWC_PROTECTION_AUTOMATICALLY = true;
    public static boolean LWC_LIMITS_BLOCK_CREATION = true;
    public static boolean WORLDGUARD_INTEGRATION = false;
    public static boolean WORLDGUARD_USE_FLAG = false;
    public static boolean WORLDGUARD_USE_PROTECTION = false;
    public static boolean GRIEFPREVENTION_INTEGRATION = false;
    public static boolean REDPROTECT_INTEGRATION = false;
    public static boolean AUTHME_HOOK = true;
    public static boolean AUTHME_ALLOW_UNREGISTERED = false;
    public static double HEROES_EXP = 100;
    public static boolean SHOWITEM_MESSAGE = true;
    public static boolean USE_STOCK_COUNTER = false;
    public static Set<String> EXCLUDED_ITEM_ATTRIBUTES = new LinkedHashSet<>();

    private Properties() {
    }

    /**
     * Refresh every static field from a loaded {@link ChestShopConfiguration}.
     * Called at enable and after a config reload.
     */
    public static void applyFrom(ChestShopConfiguration c) {
        DEBUG = c.isDebug();
        INCLUDE_SETTINGS_IN_METRICS = c.isIncludeSettingsInMetrics();
        CACHE_SIZE = c.getCacheSize();
        DEFAULT_LANGUAGE = c.getDefaultLanguage();
        USE_CLIENT_LOCALE = c.isUseClientLocale();
        STRIP_PRICE_COLORS = c.isStripPriceColors();
        SHOP_CONTAINERS = c.getShopContainers();
        SHOP_INTERACTION_INTERVAL = c.getShopInteractionInterval();
        IGNORE_CREATIVE_MODE = c.isIgnoreCreativeMode();
        IGNORE_ACCESS_PERMS = c.isIgnoreAccessPerms();
        REVERSE_BUTTONS = c.isReverseButtons();
        SHIFT_SELLS_IN_STACKS = c.isShiftSellsInStacks();
        SHIFT_SELLS_EVERYTHING = c.isShiftSellsEverything();
        SHIFT_ALLOWS = c.getShiftAllows();
        ALLOW_SIGN_CHEST_OPEN = c.isAllowSignChestOpen();
        SIGN_DYING = c.isSignDying();
        ALLOW_LEFT_CLICK_DESTROYING = c.isAllowLeftClickDestroying();
        REMOVE_EMPTY_SHOPS = c.isRemoveEmptyShops();
        REMOVE_EMPTY_CHESTS = c.isRemoveEmptyChests();
        REMOVE_EMPTY_WORLDS = c.getRemoveEmptyWorlds();
        ADMIN_SHOP_NAME = c.getAdminShopName();
        FORCE_UNLIMITED_ADMIN_SHOP = c.isForceUnlimitedAdminShop();
        SERVER_ECONOMY_ACCOUNT = c.getServerEconomyAccount();
        SERVER_ECONOMY_ACCOUNT_UUID = c.getServerEconomyAccountUuid();
        TAX_AMOUNT = c.getTaxAmount();
        SERVER_TAX_AMOUNT = c.getServerTaxAmount();
        SHOP_CREATION_PRICE = c.getShopCreationPrice();
        SHOP_REFUND_PRICE = c.getShopRefundPrice();
        PRICE_PRECISION = c.getPricePrecision();
        ENSURE_CORRECT_PLAYERID = c.isEnsureCorrectPlayerid();
        VALID_PLAYERNAME_REGEXP = c.getValidPlayernameRegexp();
        BLOCK_SHOPS_WITH_SELL_PRICE_HIGHER_THAN_BUY_PRICE = c.isBlockShopsWithSellPriceHigherThanBuyPrice();
        ALLOW_FREE_SHOPS = c.isAllowFreeShops();
        MAX_SHOP_AMOUNT = c.getMaxShopAmount();
        ALLOW_MULTIPLE_SHOPS_AT_ONE_BLOCK = c.isAllowMultipleShopsAtOneBlock();
        ALLOW_PARTIAL_TRANSACTIONS = c.isAllowPartialTransactions();
        ALLOW_AUTO_ITEM_FILL = c.isAllowAutoItemFill();
        BUNGEECORD_MESSAGES = c.isBungeecordMessages();
        SHOW_MESSAGE_OUT_OF_STOCK = c.isShowMessageOutOfStock();
        SHOW_MESSAGE_FULL_SHOP = c.isShowMessageFullShop();
        NOTIFICATION_MESSAGE_COOLDOWN = c.getNotificationMessageCooldown();
        CSTOGGLE_TOGGLES_OUT_OF_STOCK = c.isCstoggleTogglesOutOfStock();
        CSTOGGLE_TOGGLES_FULL_SHOP = c.isCstoggleTogglesFullShop();
        SHOW_TRANSACTION_INFORMATION_CLIENT = c.isShowTransactionInformationClient();
        SHOW_TRANSACTION_INFORMATION_OWNER = c.isShowTransactionInformationOwner();
        LOG_TO_FILE = c.isLogToFile();
        LOG_TO_CONSOLE = c.isLogToConsole();
        LOG_ALL_SHOP_REMOVALS = c.isLogAllShopRemovals();
        STACK_TO_64 = c.isStackTo64();
        USE_BUILT_IN_PROTECTION = c.isUseBuiltInProtection();
        STICK_SIGNS_TO_CHESTS = c.isStickSignsToChests();
        TURN_OFF_DEFAULT_PROTECTION_WHEN_PROTECTED_EXTERNALLY = c.isTurnOffDefaultProtectionWhenProtectedExternally();
        TURN_OFF_SIGN_PROTECTION = c.isTurnOffSignProtection();
        TURN_OFF_HOPPER_PROTECTION = c.isTurnOffHopperProtection();
        CHECK_ACCESS_FOR_SHOP_USE = c.isCheckAccessForShopUse();
        PROTECT_CHEST_WITH_LWC = c.isProtectChestWithLwc();
        LWC_CHEST_PROTECTION_TYPE = c.getLwcChestProtectionType();
        PROTECT_SIGN_WITH_LWC = c.isProtectSignWithLwc();
        LWC_SIGN_PROTECTION_TYPE = c.getLwcSignProtectionType();
        REMOVE_LWC_PROTECTION_AUTOMATICALLY = c.isRemoveLwcProtectionAutomatically();
        LWC_LIMITS_BLOCK_CREATION = c.isLwcLimitsBlockCreation();
        WORLDGUARD_INTEGRATION = c.isWorldguardIntegration();
        WORLDGUARD_USE_FLAG = c.isWorldguardUseFlag();
        WORLDGUARD_USE_PROTECTION = c.isWorldguardUseProtection();
        GRIEFPREVENTION_INTEGRATION = c.isGriefpreventionIntegration();
        REDPROTECT_INTEGRATION = c.isRedprotectIntegration();
        AUTHME_HOOK = c.isAuthmeHook();
        AUTHME_ALLOW_UNREGISTERED = c.isAuthmeAllowUnregistered();
        HEROES_EXP = c.getHeroesExp();
        SHOWITEM_MESSAGE = c.isShowitemMessage();
        USE_STOCK_COUNTER = c.isUseStockCounter();
        EXCLUDED_ITEM_ATTRIBUTES = c.getExcludedItemAttributes();
    }
}
