package io.paradaux.chestshop.services;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.paradaux.chestshop.model.config.ChestShopConfiguration;
import io.paradaux.chestshop.utils.Messages;
import io.paradaux.hibernia.framework.i18n.Message;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

/**
 * Cross-server (BungeeCord) shop-trade notifications: renders a message and forwards it to
 * the named recipient on another backend via the {@code BungeeCord} plugin-messaging channel
 * (config-gated by {@code BUNGEECORD_MESSAGES}). Extracted from the ChestShop main class's
 * static {@code sendBungeeMessage} methods into an injected service (PAR-300); the unused
 * {@code String}/{@code BaseComponent[]} overloads were dropped.
 *
 * @author Acrobot
 */
@Singleton
public class BungeeMessenger {

    private final Message message;
    private final ChestShopConfiguration config;
    private final JavaPlugin plugin;

    @Inject
    public BungeeMessenger(Message message, ChestShopConfiguration config, JavaPlugin plugin) {
        this.message = message;
        this.config = config;
        this.plugin = plugin;
    }

    /** Render {@code key} (with the given replacements) and send it to {@code playerName}. */
    public void send(String playerName, String key, Map<String, String> replacementMap, String... replacements) {
        send(playerName, message.component(key, Messages.values(true, replacementMap, replacements)));
    }

    /** Send an already-rendered component to {@code playerName}. */
    public void send(String playerName, Component message) {
        send(playerName, "MessageRaw", GsonComponentSerializer.gson().serialize(message));
    }

    private void send(String playerName, String channel, String rendered) {
        if (config.isBungeecordMessages() && !Bukkit.getOnlinePlayers().isEmpty()) {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF(channel);
            out.writeUTF(playerName);
            out.writeUTF(rendered);

            Bukkit.getOnlinePlayers().iterator().next().sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
        }
    }
}
