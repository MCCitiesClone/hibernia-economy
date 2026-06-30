package io.paradaux.chestshop.signs;

import io.paradaux.chestshop.utils.BlockUtil;
import io.paradaux.chestshop.utils.ImplementationAdapter;
import io.paradaux.chestshop.utils.QuantityUtil;
import io.paradaux.chestshop.utils.StringUtil;
import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.configuration.Properties;
import io.paradaux.chestshop.economy.AdminInventory;
import io.paradaux.chestshop.database.Account;
import io.paradaux.chestshop.permission.Permissions;
import io.paradaux.chestshop.utils.uBlock;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import static io.paradaux.chestshop.utils.ImplementationAdapter.getState;
import static io.paradaux.chestshop.utils.MaterialUtil.DURABILITY;
import static io.paradaux.chestshop.utils.MaterialUtil.METADATA;

/**
 * @author Acrobot
 */
public class ChestShopSign {
    public static final byte NAME_LINE = 0;
    public static final byte QUANTITY_LINE = 1;
    public static final byte PRICE_LINE = 2;
    public static final byte ITEM_LINE = 3;

    public static final Pattern[][] SHOP_SIGN_PATTERN = {
            { Pattern.compile("^[1-9][0-9]{0,5}$"), QuantityUtil.QUANTITY_LINE_WITH_COUNTER_PATTERN },
            {
                Pattern.compile("(?i)^((\\d*([.e]\\d+)?)([KM])?|free)$"),
                Pattern.compile("(?i)^([BS] *((\\d+([.e]\\d+)?([KM])?)|free))( *: *([BS] *((\\d+([.e]\\d+)?([KM])?)|free)))?$"),
                Pattern.compile("(?i)^(((\\d+([.e]\\d+)?([KM] )?)|free) *[BS])( *: *([BS] *((\\d+([.e]\\d+)?([KM])?)|free)))?$"),
                Pattern.compile("(?i)^(((\\d+([.e]\\d+)?([KM] )?)|free) *[BS]) *: *(((\\d+([.e]\\d+)?([KM] )?)|free) *[BS])$"),
                Pattern.compile("(?i)^([BS] *((\\d+([.e]\\d+)?([KM])?)|free)) *: *(((\\d+([.e]\\d+)?([KM] )?)|free) *[BS])$"),
            },
            {
                Pattern.compile("^\\?$"),
                Pattern.compile("^[\\p{L}\\d_ \\-]+(" + DURABILITY.pattern() + ")?(" + METADATA.pattern() + ")?$"),
                Pattern.compile("^[\\p{L}\\d_ \\-]+" + METADATA.pattern() + "(" + DURABILITY.pattern() + ")?$")
            }
    };
    public static final String AUTOFILL_CODE = "?";

    private static final Pattern BUSINESS_PATTERN = Pattern.compile("(?i)^B:[0-9A-Z]+$");

    public static boolean isBusinessAccount(String owner) {
        return BUSINESS_PATTERN.matcher(owner).matches();
    }

    public static boolean isBusinessAccount(String[] lines) {
        return isBusinessAccount(getOwner(lines));
    }

    public static int getBusinessAccountId(String owner) {
        return Integer.parseInt(owner.substring(2), 36);
    }

    public static String businessAccountSignName(int accountId) {
        return "B:" + Integer.toString(accountId, 36).toUpperCase(Locale.ROOT);
    }

    public static boolean isAdminShop(Inventory ownerInventory) {
        return ownerInventory instanceof AdminInventory;
    }

    public static boolean isAdminShop(String owner) {
        return owner.replace(" ", "").equalsIgnoreCase(Properties.ADMIN_SHOP_NAME.replace(" ", ""));
    }

    public static boolean isAdminShop(Sign sign) {
        return isAdminShop(sign.getLines());
    }

    public static boolean isAdminShop(String[] lines) {
        return isAdminShop(getOwner(lines));
    }

    public static boolean isValid(Sign sign) {
        return isValid(sign.getLines());
    }

    public static boolean isValid(String[] lines) {
        lines = StringUtil.stripColourCodes(lines);
        return ChestShop.items().validateSign(lines)
                && (getPrice(lines).toUpperCase(Locale.ROOT).contains("B")
                        || getPrice(lines).toUpperCase(Locale.ROOT).contains("S"))
                && !getOwner(lines).isEmpty();
    }

    public static boolean isValid(Block sign) {
        return BlockUtil.isSign(sign) && isValid((Sign) getState(sign, false));
    }

    /**
     * @deprecated Use {@link #isShopBlock(Block}
     */
    @Deprecated
    public static boolean isShopChest(Block chest) {
        if (!BlockUtil.isChest(chest)) {
            return false;
        }

        return uBlock.getConnectedSign(chest) != null;
    }

    public static boolean isShopBlock(Block block) {
        if (!uBlock.couldBeShopContainer(block)) {
            return false;
        }

        return uBlock.getConnectedSign(block) != null;
    }

    /**
     * @deprecated Use {@link #isShopBlock(InventoryHolder}
     */
    @Deprecated
    public static boolean isShopChest(InventoryHolder holder) {
        if (!BlockUtil.isChest(holder)) {
            return false;
        }

        if (holder instanceof DoubleChest) {
            return isShopChest(((DoubleChest) holder).getLocation().getBlock());
        } else if (holder instanceof Chest) {
            return isShopChest(((Chest) holder).getBlock());
        } else {
            return false;
        }
    }

    public static boolean isShopBlock(InventoryHolder holder) {
        if (holder instanceof DoubleChest) {
            return isShopBlock(ImplementationAdapter.getLeftSide((DoubleChest) holder, false))
                    || isShopBlock(ImplementationAdapter.getRightSide((DoubleChest) holder, false));
        } else if (holder instanceof BlockState) {
            return isShopBlock(((BlockState) holder).getBlock());
        }
        return false;
    }

    public static Block getShopBlock(InventoryHolder holder) {
        if (holder instanceof DoubleChest) {
            return Optional.ofNullable(getShopBlock(ImplementationAdapter.getLeftSide((DoubleChest) holder, false)))
                    .orElse(getShopBlock(ImplementationAdapter.getRightSide((DoubleChest) holder, false)));
        } else if (holder instanceof BlockState) {
            return ((BlockState) holder).getBlock();
        }
        return null;
    }

    // canAccess / hasPermission / isOwner (the account-access predicates) moved to
    // AccountService (PAR-282) — they were ledger logic living on a sign util.

    /**
     * @deprecated Call {@code ChestShop.items().validateSign(...)}
     *             ({@link io.paradaux.chestshop.services.ItemService#validateSign}) instead.
     */
    @Deprecated
    public static boolean isValidPreparedSign(String[] lines) {
        return ChestShop.items().validateSign(lines);
    }

    /**
     * Get the owner string of a shop sign
     * @param sign The sign
     * @return The owner string
     */
    public static String getOwner(Sign sign) {
        return getOwner(sign.getLines());
    }

    /**
     * Get the owner string of a shop sign
     * @param lines The sign lines
     * @return The owner string
     */
    public static String getOwner(String[] lines) {
        return StringUtil.stripColourCodes(StringUtil.strip(StringUtil.stripColourCodes(lines[NAME_LINE])));
    }

    /**
     * Get the quantity and count line of the shop sign
     * @param sign The sign
     * @return The quantity line
     * @throws IllegalArgumentException Thrown when an invalid quantity is present
     */
    public static String getQuantityLine(Sign sign) throws IllegalArgumentException {
        return getQuantityLine(sign.getLines());
    }

    /**
     * Get the quantity and count line of sign lines
     * @param lines The sign lines
     * @return The quantity line
     * @throws IllegalArgumentException Thrown when an invalid quantity is present
     */
    public static String getQuantityLine(String[] lines) throws IllegalArgumentException {
        return lines.length > QUANTITY_LINE ? StringUtil.strip(StringUtil.stripColourCodes(lines[QUANTITY_LINE])) : "";
    }

    /**
     * Get the quantity of the shop sign
     * @param sign The sign
     * @return The quantity line
     * @throws IllegalArgumentException Thrown when an invalid quantity is present
     */
    public static int getQuantity(Sign sign) throws IllegalArgumentException {
        return getQuantity(sign.getLines());
    }

    /**
     * Get the quantity of sign lines
     * @param lines The sign lines
     * @return The quantity
     * @throws IllegalArgumentException Thrown when an invalid quantity is present
     */
    public static int getQuantity(String[] lines) throws IllegalArgumentException {
        return QuantityUtil.parseQuantity(getQuantityLine(lines));
    }

    /**
     * Get the price line of the shop sign
     * @param sign The sign
     * @return The price line
     */
    public static String getPrice(Sign sign) {
        return StringUtil.strip(StringUtil.stripColourCodes(sign.getLine(PRICE_LINE)));
    }

    /**
     * Get the price line of sign lines
     * @param lines The sign lines
     * @return The price line
     */
    public static String getPrice(String[] lines) {
        return lines.length > PRICE_LINE ? StringUtil.strip(StringUtil.stripColourCodes(lines[PRICE_LINE])) : "";
    }

    /**
     * Get the item line of the shop sign
     * @param sign The sign
     * @return The item line
     */
    public static String getItem(Sign sign) {
        return getItem(sign.getLines());
    }

    /**
     * Get the item line of sign lines
     * @param lines The sign lines
     * @return The item line
     */
    public static String getItem(String[] lines) {
        return lines.length > ITEM_LINE ? StringUtil.strip(StringUtil.stripColourCodes(lines[ITEM_LINE])) : "";
    }
}
