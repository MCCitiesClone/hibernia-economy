package io.paradaux.chestshop.commands;
import io.paradaux.chestshop.utils.StringUtil;

import io.paradaux.chestshop.dialogs.FindDialog;
import io.paradaux.chestshop.dialogs.FindState;
import com.google.inject.Inject;
import io.paradaux.chestshop.services.PreviewService;
import io.paradaux.chestshop.services.MarketService;
import io.paradaux.chestshop.services.MarketResyncService;
import io.paradaux.chestshop.utils.Permissions;
import io.paradaux.chestshop.services.ItemCodeService;
import io.paradaux.chestshop.services.ItemService;
import io.paradaux.chestshop.services.SignService;
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
    private final SignService signService;
    private final MarketResyncService resync;
    private final PreviewService previews;
    private final Message message;

    private final io.paradaux.chestshop.services.MarketService marketService;

    @Inject
    public FindCommand(DialogManager dialogs, ItemService items, ItemCodeService itemCodes,
                       SignService signService, MarketResyncService resync,
                       PreviewService previews, Message message, MarketService marketService) {
        this.marketService = marketService;
        this.dialogs = dialogs;
        this.items = items;
        this.itemCodes = itemCodes;
        this.signService = signService;
        this.resync = resync;
        this.previews = previews;
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
        if (!marketService.enabled()) {
            message.send(player, "find.no-search");
            return;
        }
        Location l = sign.getLocation();
        marketService.market().setShopVisibility(l.getWorld().getName(),
                l.getBlockX(), l.getBlockY(), l.getBlockZ(), visible);
        message.send(player, "find.visibility-set", "value", visible ? "shown" : "hidden");
    }

    /**
     * {@code /find toggle hologram <true|false>} — show or hide the floating item
     * preview above the shop the player is looking at. Owner (or admin) only.
     */
    @Route("toggle hologram <visible>")
    public void toggleHologram(@Sender CommandSender sender, @Arg("visible") boolean visible) {
        if (!(sender instanceof Player player)) {
            return;
        }
        Sign sign = targetShopSign(player);
        if (sign == null) {
            return;
        }
        if (!marketService.enabled()) {
            message.send(player, "find.no-search");
            return;
        }
        Location l = sign.getLocation();
        String world = l.getWorld().getName();
        marketService.market().setShopHologram(world, l.getBlockX(), l.getBlockY(), l.getBlockZ(), visible);
        if (visible) {
            String itemCode = SignService.getItem(sign);
            ItemStack item = itemCode != null ? itemCodes.decode(itemCode) : null;
            if (item != null) {
                previews.render(world, l.getBlockX(), l.getBlockY(), l.getBlockZ(), item);
            }
        } else {
            previews.destroy(world, l.getBlockX(), l.getBlockY(), l.getBlockZ());
        }
        message.send(player, "find.hologram-set", "value", visible ? "shown" : "hidden");
    }

    /**
     * {@code /find toggle preview <true|false>} — whether this player sees shop
     * holograms at all. Persisted per-player.
     */
    @Route("toggle preview <visible>")
    public void togglePreview(@Sender CommandSender sender, @Arg("visible") boolean visible) {
        if (!(sender instanceof Player player)) {
            return;
        }
        if (!marketService.enabled()) {
            message.send(player, "find.no-search");
            return;
        }
        marketService.market().setPreviewPreference(player.getUniqueId(), visible);
        previews.applyPreference(player, visible);
        message.send(player, "find.preview-set", "value", visible ? "shown" : "hidden");
    }

    /**
     * {@code /find resync <chunksPerTick>} — rebuild the shop registry from the
     * loaded world (discovery + stock refresh + dead-shop cleanup). Admin only.
     */
    @Route("resync <chunks>")
    public void resync(@Sender CommandSender sender, @Arg("chunks") int chunks) {
        if (!(sender instanceof Player player)) {
            return;
        }
        if (!player.hasPermission(Permissions.ADMIN)) {
            message.send(player, "find.no-permission");
            return;
        }
        resync.resync(player, chunks);
    }

    /** The valid, accessible ChestShop sign the player is looking at, or null (with a message sent). */
    private Sign targetShopSign(Player player) {
        Block block = player.getTargetBlockExact(5);
        if (block == null || !Tag.SIGNS.isTagged(block.getType()) || !(block.getState(false) instanceof Sign sign)) {
            message.send(player, "find.sign-target");
            return null;
        }
        if (!signService.isValid(sign)) {
            message.send(player, "find.sign-target");
            return null;
        }
        String owner = SignService.getOwner(sign);
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
     * mirrors {@code MarketService.itemKey} so a search matches what the tracker wrote.
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
        net.kyori.adventure.text.Component display =
                item.hasItemMeta() && item.getItemMeta() != null ? item.getItemMeta().displayName() : null;
        if (display != null) {
            String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(display);
            if (!name.isBlank()) {
                return name.trim();
            }
        }
        return canonicalKey(item);
    }
}
