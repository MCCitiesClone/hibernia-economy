package io.paradaux.chestshop.commands;

import io.paradaux.chestshop.Permission;
import io.paradaux.chestshop.configuration.Messages;
import com.google.common.base.Preconditions;
import io.paradaux.hibernia.framework.commander.annotations.Command;
import io.paradaux.hibernia.framework.commander.annotations.Route;
import io.paradaux.hibernia.framework.commander.annotations.Sender;
import io.paradaux.hibernia.framework.commander.spi.CommandHandler;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * @author g--o
 */
@Command({"csaccess"})
@io.paradaux.hibernia.framework.commander.annotations.Permission(Permission.Node.ACCESS_TOGGLE)
public class AccessToggle implements CommandHandler {
    private static final Set<UUID> toggledPlayers = new HashSet<>();

    @Route("")
    public void accessToggle(@Sender Player player) {
        if (setIgnoring(player, !isIgnoring(player))) {
            Messages.TOGGLE_ACCESS_OFF.sendWithPrefix(player);
        } else {
            Messages.TOGGLE_ACCESS_ON.sendWithPrefix(player);
        }
    }

    public static boolean isIgnoring(OfflinePlayer player) {
        return player != null && isIgnoring(player.getUniqueId());
    }

    private static boolean isIgnoring(UUID playerId) {
        return toggledPlayers.contains(playerId);
    }

    public static boolean setIgnoring(Player player, boolean ignoring) {
        Preconditions.checkNotNull(player); // Make sure the player instance is not null, in case there are any errors in the code

        if (ignoring) {
            toggledPlayers.add(player.getUniqueId());
        } else {
            toggledPlayers.remove(player.getUniqueId());
        }

        return ignoring;
    }
}
