package io.paradaux.jobs.commands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.paradaux.hibernia.framework.i18n.Message;
import io.paradaux.jobs.model.ListingCommand;
import io.paradaux.jobs.services.JobListRenderer;
import io.paradaux.jobs.services.JobRegistry;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * Registers the top-level listing commands declared in {@code jobs.yml}.
 *
 * <p><strong>{@code jobs.yml} is the source of truth for which of these exist.</strong>
 * Declaring {@code qual:} creates {@code /qual}; renaming it renames the command;
 * removing it removes the command. None of that is possible with the framework's
 * {@code @Command}, whose value is a compile-time annotation literal read from
 * {@code getDeclaredMethods()} — so these are registered straight into Bukkit's
 * command map at runtime instead.</p>
 *
 * <p>Only the <em>roots</em> bypass the framework. The body delegates to the same
 * {@link JobActions} the {@code /jobs} subcommands use, so behaviour, permissions
 * and messages stay identical however a player gets there.</p>
 */
@Singleton
public final class ListingCommandRegistrar {

    /** Namespace for the fallback {@code /jobs:licenses} form Bukkit always creates. */
    private static final String FALLBACK_PREFIX = "jobs";

    private final JavaPlugin plugin;
    private final JobActions actions;
    private final Message message;
    private final JobRegistry registry;
    private final Logger log;

    /** Registered by us, so a reload can take them back out again. */
    private final List<Command> registered = new ArrayList<>();

    @Inject
    public ListingCommandRegistrar(JavaPlugin plugin, JobActions actions, Message message,
                                   JobRegistry registry) {
        this.plugin = plugin;
        this.actions = actions;
        this.message = message;
        this.registry = registry;
        this.log = plugin.getLogger();
    }

    /**
     * Re-register every configured listing command.
     *
     * <p>Safe to call again after a reload: previously-registered roots are removed
     * first, so renaming a command in {@code jobs.yml} does not leave the old name
     * behind.</p>
     */
    public synchronized void registerAll() {
        CommandMap commandMap = plugin.getServer().getCommandMap();
        unregisterAll(commandMap);

        for (ListingCommand listing : registry.snapshot().listingCommands()) {
            Command existing = commandMap.getCommand(listing.name());
            if (existing != null) {
                // Another plugin already owns the bare name; Bukkit would namespace
                // ours to /jobs:<name>, which nobody would guess. Say so plainly.
                log.warning("/" + listing.name() + " is already registered by another plugin, "
                        + "so the Jobs listing command is only reachable as /"
                        + FALLBACK_PREFIX + ":" + listing.name()
                        + ". Rename it in jobs.yml to avoid the clash.");
            }
            // register() returns false when the primary name was taken and only the
            // namespaced fallback got it. The command is live either way, so track it
            // regardless — otherwise a reload could not take it back out.
            Command command = new ListingBukkitCommand(listing);
            commandMap.register(FALLBACK_PREFIX, command);
            registered.add(command);
        }

        if (!registered.isEmpty()) {
            log.info("Registered " + registered.size() + " listing command(s) from jobs.yml: "
                    + registry.snapshot().listingCommands().stream()
                            .map(entry -> "/" + entry.name())
                            .reduce((a, b) -> a + ", " + b).orElse(""));
        }
    }

    /** Remove every root this registrar added. Called on reload and on disable. */
    public synchronized void unregisterAll() {
        unregisterAll(plugin.getServer().getCommandMap());
    }

    private void unregisterAll(CommandMap commandMap) {
        for (Command command : registered) {
            command.unregister(commandMap);
            // unregister() detaches the command but leaves the name -> command
            // entries behind, so drop those too or a rename would resurrect the old
            // root the next time something looks it up.
            commandMap.getKnownCommands().values().removeIf(known -> known == command);
        }
        registered.clear();
    }

    /**
     * A listing root. Everything it does is delegated, so this class carries no
     * behaviour of its own beyond argument shape and tab completion.
     */
    private final class ListingBukkitCommand extends Command {

        private final ListingCommand listing;

        private ListingBukkitCommand(ListingCommand listing) {
            super(listing.name(),
                    "List the " + listing.typeKey() + " a player holds",
                    "/" + listing.name() + " [player]",
                    new ArrayList<>(listing.aliases()));
            this.listing = listing;
        }

        @Override
        public boolean execute(@NotNull CommandSender sender, @NotNull String label,
                               String @NotNull [] args) {
            OfflinePlayer target = args.length > 0 ? resolveTarget(sender, args[0]) : null;
            if (args.length > 0 && target == null) {
                return true;   // resolveTarget already explained why
            }

            String node = target == null ? "jobs.use" : "jobs.list.other";
            if (!CommandSupport.isAllowed(sender, node)) {
                message.send(sender, "jobs.error.no-permission");
                return true;
            }

            // Off the main thread: listing reads LuckPerms, which may hit storage.
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () ->
                    actions.listByType(sender, listing.typeKey(), target));
            return true;
        }

        private OfflinePlayer resolveTarget(CommandSender sender, String name) {
            // Cache-only, matching the framework's own player resolver: never block
            // on Mojang, and never fabricate a UUID for a name nobody has seen.
            OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(name);
            if (target == null) {
                message.send(sender, "jobs.error.unknown-player", "player", name);
            }
            return target;
        }

        @Override
        public @NotNull List<String> tabComplete(@NotNull CommandSender sender,
                                                 @NotNull String alias,
                                                 String @NotNull [] args) {
            if (args.length != 1) {
                return List.of();
            }
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> names = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    names.add(online.getName());
                }
            }
            return names;
        }
    }
}
