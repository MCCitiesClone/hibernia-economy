package io.paradaux.chestshop.services;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.paradaux.chestshop.utils.MaterialUtil;
import io.paradaux.chestshop.utils.PriceUtil;
import io.paradaux.business.api.BusinessApi;
import io.paradaux.business.model.Firm;
import io.paradaux.treasury.api.TreasuryApi;
import io.paradaux.treasury.api.market.ChestShopSaleRecord;
import io.paradaux.treasury.api.market.ChestShopShopRecord;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Sign;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Builds the MarketApi DTOs from ChestShop data: classifies the owning account
 * (personal / business firm / government / admin) via the Treasury + Business
 * APIs, and resolves the real item (incl. custom items) via ChestShop's own
 * item encoding. Injected so the custom-item resolution goes through {@link ItemService}
 * rather than the static locator (PAR-282).
 */
@Singleton
public class MarketRecords {

    /** Mirrors ChestShop's synthetic-UUID scheme for business accounts: UUID(MSB, accountId). */
    static final long BUSINESS_UUID_MSB = 0xC5B0000000000000L;

    private final ItemService items;
    private final ItemCodeService itemCodes;
    private final InventoryService inventoryService;

    @Inject
    MarketRecords(ItemService items, ItemCodeService itemCodes, InventoryService inventoryService) {
        this.items = items;
        this.itemCodes = itemCodes;
        this.inventoryService = inventoryService;
    }

    public record Owner(Integer accountId, String type, Integer firmId, UUID ownerUuid, boolean admin) {}

    /** Resolve the shop's owning account from the ChestShop owner-account UUID. */
    public Owner ownerFromUuid(UUID ownerUuid, boolean adminShop) {
        if (adminShop || ownerUuid == null) {
            return new Owner(null, null, null, null, true);
        }
        TreasuryApi treasury = MarketHook.treasury();
        int accountId;
        if (ownerUuid.getMostSignificantBits() == BUSINESS_UUID_MSB) {
            accountId = (int) ownerUuid.getLeastSignificantBits();
        } else {
            io.paradaux.treasury.model.economy.Account personal = treasury.getAccountByUUID(ownerUuid);
            if (personal == null) {
                return new Owner(null, null, null, null, false);
            }
            accountId = personal.getAccountId();
        }
        return classify(accountId);
    }

    Owner classify(int accountId) {
        io.paradaux.treasury.model.economy.Account acc = MarketHook.treasury().getAccountById(accountId);
        if (acc == null) {
            return new Owner(accountId, null, null, null, false);
        }
        String type = acc.getAccountType() != null ? acc.getAccountType().name() : null;
        UUID ownerUuid = "PERSONAL".equals(type) ? acc.getOwnerUuid() : null;
        Integer firmId = null;
        BusinessApi business = MarketHook.business();
        if ("BUSINESS".equals(type) && business != null) {
            Firm firm = business.firms().getFirmByAccountId(accountId);
            if (firm != null) {
                firmId = firm.getFirmId();
            }
        }
        return new Owner(accountId, type, firmId, ownerUuid, false);
    }

    // ── item identity ──────────────────────────────────────────────────────
    // Route through ChestShop's ItemUtil rather than the raw Breeze MaterialUtil,
    // so custom items resolve to their real code instead of the underlying vanilla
    // material. items.getName goes through ItemService.queryString — the same
    // resolver the server's custom-item bridge (e.g. the Nexo integration plugin)
    // hooks to name/parse its items. When no provider claims the item, it falls
    // back to the vanilla code, so vanilla behaviour is unchanged.

    /**
     * ChestShop's canonical item code for this stack (custom-aware), or
     * {@code null} if it can't be round-tripped — width 0 means full, untruncated
     * fidelity (no sign-width shortening, which the markets DB doesn't need).
     */
    private String canonicalCode(ItemStack item) {
        try {
            return items.getName(item, 0);
        } catch (RuntimeException e) {
            // getName re-parses the code and throws if it doesn't round-trip
            // (e.g. provider present for query but not parse). Fall back below.
            return null;
        }
    }

    /** Stable grouping key — ChestShop's canonical (custom-aware) sign code. */
    String itemKey(ItemStack item) {
        String code = canonicalCode(item);
        return code != null ? code : itemCodes.encode(item, MaterialUtil.MAXIMUM_SIGN_WIDTH);
    }

    /**
     * Human-readable label for the markets UI. Prefers the item's own display
     * name (an anvil-renamed or custom item), colour codes stripped; failing
     * that, for a custom item it prettifies the provider code (so a Nexo item
     * with no display name still reads as its custom id, not the base material);
     * otherwise a prettified material ("DIAMOND_SWORD" -> "Diamond Sword").
     * Distinct from {@link #itemKey} — the key stays the stable code for
     * grouping, while this is just for display.
     */
    String itemName(ItemStack item) {
        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                String name = ChatColor.stripColor(meta.getDisplayName());
                if (name != null && !name.trim().isEmpty()) {
                    return name.trim();
                }
            }
        }
        if (isCustom(item)) {
            String code = canonicalCode(item);
            if (code != null && !code.isBlank()) {
                return pretty(code);
            }
        }
        return pretty(item.getType().name());
    }

    /** "DIAMOND_SWORD" / "nexo:ruby_sword#02" -> "Diamond Sword" / "Ruby Sword". */
    private static String pretty(String raw) {
        if (raw == null) return null;
        String base = raw;
        int hash = base.indexOf('#');
        if (hash >= 0) base = base.substring(0, hash);     // drop the metadata code
        int colon = base.lastIndexOf(':');
        if (colon >= 0) base = base.substring(colon + 1);  // drop any namespace
        StringBuilder sb = new StringBuilder();
        for (String word : base.toLowerCase(Locale.ROOT).split("[_\\s]+")) {
            if (word.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.length() > 0 ? sb.toString() : raw;
    }

    boolean isCustom(ItemStack item) {
        String vanilla = itemCodes.encode(item, MaterialUtil.MAXIMUM_SIGN_WIDTH);
        String canonical = canonicalCode(item);
        // A provider (Nexo) named it beyond the plain vanilla code.
        if (canonical != null && vanilla != null && !canonical.equalsIgnoreCase(vanilla)) {
            return true;
        }
        return (vanilla != null && vanilla.contains("#")) || item.hasItemMeta();
    }

    String itemData(ItemStack item) {
        if (!isCustom(item)) return null;
        try {
            YamlConfiguration yc = new YamlConfiguration();
            yc.set("item", item);
            return yc.saveToString();
        } catch (Throwable t) {
            return null;
        }
    }

    public int stockOf(ItemStack item, Inventory inventory) {
        return inventory == null ? 0 : inventoryService.getAmount(item, inventory);
    }

    /** Remaining free space for the item in the container; null if unknown (no inventory). */
    public Integer capacityOf(ItemStack item, Inventory inventory) {
        return inventory == null ? null : inventoryService.getRemainingCapacity(item, inventory);
    }

    private static BigDecimal nonNegativeOrNull(BigDecimal v) {
        return (v == null || v.signum() < 0) ? null : v;
    }

    // ── DTO builders ──
    public ChestShopSaleRecord sale(Sign sign, ItemStack item, int quantity, UUID customer,
                                    Owner owner, BigDecimal total, BigDecimal tax,
                                    String direction, Integer shopStock, Long txnId) {
        Location l = sign.getLocation();
        BigDecimal unit = quantity > 0
                ? total.divide(BigDecimal.valueOf(quantity), 4, RoundingMode.HALF_UP)
                : total;
        return new ChestShopSaleRecord(
                txnId, direction, customer,
                owner.accountId(), owner.type(), owner.firmId(), owner.ownerUuid(), owner.admin(),
                item.getType().name(), itemKey(item), itemName(item), isCustom(item), itemData(item),
                quantity, unit, total, tax != null ? tax : BigDecimal.ZERO,
                worldName(l), l.getBlockX(), l.getBlockY(), l.getBlockZ(),
                owner.admin() ? null : shopStock);
    }

    public ChestShopShopRecord shop(Sign sign, ItemStack item, Owner owner,
                             Integer currentStock, Integer estimatedCapacity) {
        Location l = sign.getLocation();
        String priceLine = sign.getLine(ChestShopSign.PRICE_LINE);
        int batch;
        try {
            batch = ChestShopSign.getQuantity(sign);
        } catch (RuntimeException e) {
            batch = Math.max(1, item.getAmount());
        }
        return new ChestShopShopRecord(
                worldName(l), l.getBlockX(), l.getBlockY(), l.getBlockZ(), owner.admin(),
                owner.accountId(), owner.type(), owner.firmId(), owner.ownerUuid(),
                item.getType().name(), itemKey(item), itemName(item), isCustom(item), itemData(item),
                nonNegativeOrNull(PriceUtil.getExactBuyPrice(priceLine)),
                nonNegativeOrNull(PriceUtil.getExactSellPrice(priceLine)),
                batch, owner.admin() ? null : currentStock,
                owner.admin() ? null : estimatedCapacity, worldUuid(l));
    }

    public int totalAmount(ItemStack[] stock) {
        return java.util.Arrays.stream(stock).filter(Objects::nonNull).mapToInt(ItemStack::getAmount).sum();
    }

    private static String worldName(Location l) {
        return l.getWorld() != null ? l.getWorld().getName() : null;
    }

    private static UUID worldUuid(Location l) {
        return l.getWorld() != null ? l.getWorld().getUID() : null;
    }
}
