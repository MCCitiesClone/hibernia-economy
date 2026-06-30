package io.paradaux.chestshop.commands;

import com.google.inject.Inject;
import io.paradaux.chestshop.services.ItemService;
import io.paradaux.chestshop.utils.InventoryUtil;
import io.paradaux.chestshop.utils.MaterialUtil;
import io.paradaux.chestshop.utils.NumberUtil;
import io.paradaux.hibernia.framework.commander.annotations.Command;
import io.paradaux.hibernia.framework.commander.annotations.Description;
import io.paradaux.hibernia.framework.commander.annotations.GreedyArg;
import io.paradaux.hibernia.framework.commander.annotations.Route;
import io.paradaux.hibernia.framework.commander.annotations.Sender;
import io.paradaux.hibernia.framework.commander.spi.CommandHandler;
import io.paradaux.hibernia.framework.i18n.Message;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Acrobot
 */
@Command({"chestshop", "cs"})
@io.paradaux.hibernia.framework.commander.annotations.Permission("ChestShop.admin")
public class Give implements CommandHandler {

    private final ItemService items;
    private final Message message;
    private final InventoryUtil inventoryUtil;

    @Inject
    public Give(ItemService items, Message message, InventoryUtil inventoryUtil) {
        this.items = items;
        this.message = message;
        this.inventoryUtil = inventoryUtil;
    }

    @Route("give <args>")
    @Description("Give a player an item by its ChestShop item code")
    public void give(@Sender CommandSender sender,
                     @GreedyArg(value = "args", sanitize = false) String argLine) {
        // The greedy arg captures the whole tail; split it back into the token
        // array the original variadic parser expects (whitespace-delimited).
        String[] args = argLine.trim().split("\\s+");
        if (args.length < 1 || args[0].isEmpty()) {
            return;
        }

        Player receiver = (sender instanceof Player ? (Player) sender : null);
        int quantity = 1;

        Set<Integer> disregardedIndexes = new HashSet<>();

        if (args.length > 1) {
            for (int index = args.length - 1; index >= 0; --index) {
                Player target = Bukkit.getPlayer(args[index]);

                if (target == null) {
                    continue;
                }

                receiver = target;
                disregardedIndexes.add(index);
                break;
            }

            for (int index = args.length - 1; index >= 0; --index) {
                if (disregardedIndexes.contains(index) || !NumberUtil.isInteger(args[index]) || Integer.parseInt(args[index]) < 0) {
                    continue;
                }

                quantity = Integer.parseInt(args[index]);
                disregardedIndexes.add(index);

                break;
            }
        }

        if (receiver == null) {
            message.send(sender, "chestshop.PLAYER_NOT_FOUND");
            return;
        }

        ItemStack item = getItem(args, disregardedIndexes);

        if (MaterialUtil.isEmpty(item)) {
            message.send(sender, "chestshop.INCORRECT_ITEM_ID");
            return;
        }

        item.setAmount(quantity);
        inventoryUtil.add(item, receiver.getInventory());

        message.send(sender, "chestshop.ITEM_GIVEN", "prefix", "", "item", items.getName(item), "player", receiver.getName());
    }

    private ItemStack getItem(String[] arguments, Set<Integer> disregardedElements) {
        StringBuilder builder = new StringBuilder(arguments.length * 5);

        for (int index = 0; index < arguments.length; ++index) {
            if (disregardedElements.contains(index)) {
                continue;
            }

            builder.append(arguments[index]).append(' ');
        }

        return items.parse(builder.toString());
    }
}
