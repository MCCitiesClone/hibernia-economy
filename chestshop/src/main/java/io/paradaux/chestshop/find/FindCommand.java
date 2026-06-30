package io.paradaux.chestshop.find;

import com.google.inject.Inject;
import io.paradaux.chestshop.market.MarketHook;
import io.paradaux.chestshop.permission.Permissions;
import io.paradaux.chestshop.services.ItemCodeService;
import io.paradaux.chestshop.services.ItemService;
import io.paradaux.chestshop.signs.ChestShopSign;
import io.paradaux.chestshop.utils.MaterialUtil;
import io.paradaux.hibernia.framework.commander.annotations.Arg;
import io.paradaux.hibernia.framework.commander.annotations.Command;
import io.paradaux.hibernia.framework.commander.annotations.GreedyArg;
import io.paradaux.hibernia.framework.commander.annotations.Permission;
import io.paradaux.hibernia.framework.commander.annotations.Route;
import io.paradaux.hibernia.framework.commander.annotations.Sender;
import io.paradaux.hibernia.framework.commander.spi.CommandHandler;
import io.paradaux.hibernia.framework.i18n.Message;
import io.paradaux.hibernia.framework.usher.DialogManager;
import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * {@code /find} — search the live shop registry for shops trading an item and
 * open the Usher find dialog. With no argument it uses the item in the player's
 * main hand; {@code /find <itemCode>} resolves a typed code (custom-aware).
 */
@Command("find")
@Permission("ChestShop.find")
public final class FindCommand implements CommandHandler {

    private final DialogManager dialogs;
    private final ItemService items;
    private final ItemCodeService itemCodes;
    private final ChestShopSign chestShopSign;
    private final Message message;

    @Inject
    public FindCommand(DialogManager dialogs, ItemService items, ItemCodeService itemCodes,
                       ChestShopSign chestShopSign, Message message) {
        this.dialogs = dialogs;
        this.items = items;
        this.itemCodes = itemCodes;
        this.chestShopSign = chestShopSign;
        this.message = message;
    }

    @Route("")
    public void heldItem(@Sender CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand().asOne();
        if (item == null || item.getType().isAir()) {
            message.send(player, "find.need-item");
            return;
        }
        openFind(player, item, canonicalKey(item), displayName(item));
    }

    @Route("<item>")
    public void byCode(@Sender CommandSender sender,
                       @GreedyArg(value = "item", sanitize = false) String code) {
        if (!(sender instanceof Player player)) {
            return;
        }
        ItemStack item = items.parse(code);
        if (item == null || item.getType().isAir()) {
            message.send(player, "find.unknown-item", "code", code);
            return;
        }
        openFind(player, item, canonicalKey(item), code);
    }

    /**
     * {@code /find toggle visibility <true|false>} — hide or show the shop the
     * player is looking at from {@code /find} results. Owner (or admin) only.
     */
    @Route("toggle visibility <visible>")
    public void toggleVisibility(@Sender CommandSender sender, @Arg("visible") boolean visible) {
        if (!(sender instanceof Player player)) {
            return;
        }
        Sign sign = targetShopSign(player);
        if (sign == null) {
            return; // a reason was already sent
        }
        if (!MarketHook.enabled()) {
            message.send(player, "find.no-search");
            return;
        }
        Location l = sign.getLocation();
        MarketHook.market().setShopVisibility(l.getWorld().getName(),
                l.getBlockX(), l.getBlockY(), l.getBlockZ(), visible);
        message.send(player, "find.visibility-set", "value", visible ? "shown" : "hidden");
    }

    /** The valid, accessible ChestShop sign the player is looking at, or null (with a message sent). */
    private Sign targetShopSign(Player player) {
        Block block = player.getTargetBlockExact(5);
        if (block == null || !Tag.SIGNS.isTagged(block.getType()) || !(block.getState(false) instanceof Sign sign)) {
            message.send(player, "find.sign-target");
            return null;
        }
        if (!chestShopSign.isValid(sign)) {
            message.send(player, "find.sign-target");
            return null;
        }
        String owner = ChestShopSign.getOwner(sign);
        if (!player.getName().equalsIgnoreCase(owner) && !player.hasPermission(Permissions.ADMIN)) {
            message.send(player, "find.sign-no-access");
            return null;
        }
        return sign;
    }

    private void openFind(Player player, ItemStack item, String itemKey, String displayName) {
        Location loc = player.getLocation();
        FindState state = new FindState(item, itemKey, displayName,
                player.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), true);
        dialogs.open(player, FindDialog.class, state);
    }

    /**
     * The canonical (custom-aware) sign code stored on {@code chestshop_shop.item_key} —
     * mirrors {@code MarketRecords.itemKey} so a search matches what the tracker wrote.
     */
    private String canonicalKey(ItemStack item) {
        try {
            String code = items.getName(item, 0);
            if (code != null && !code.isBlank()) {
                return code;
            }
        } catch (RuntimeException ignored) {
            // getName re-parses the code and throws if it doesn't round-trip; fall back.
        }
        return itemCodes.encode(item, MaterialUtil.MAXIMUM_SIGN_WIDTH);
    }

    private String displayName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta() != null && item.getItemMeta().hasDisplayName()) {
            String name = org.bukkit.ChatColor.stripColor(item.getItemMeta().getDisplayName());
            if (name != null && !name.isBlank()) {
                return name.trim();
            }
        }
        return canonicalKey(item);
    }
}
