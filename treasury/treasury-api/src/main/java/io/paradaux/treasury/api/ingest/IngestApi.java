package io.paradaux.treasury.api.ingest;

import org.bukkit.command.CommandSender;

import java.util.Set;

/**
 * Implemented by plugins that ingest player + balance data from a legacy
 * economy backend into Treasury (e.g. EssentialsX userdata, the original
 * DemocracyTreasury, the prior DemocracyBusiness plugin).
 *
 * <p>Implementations register with Bukkit's {@code ServicesManager}. The
 * Treasury plugin's {@code /treasury admin ingest <source>} command iterates
 * registered providers and dispatches to the one whose
 * {@link #supportedSources()} contains the requested {@code source}.
 *
 * <p>This indirection keeps Treasury free of any source-specific parsing
 * code: a new ingester (e.g. for an old SQLite economy) ships as its own
 * standalone plugin and is automatically reachable via the same admin
 * command.
 */
public interface IngestApi {

    /**
     * Names of the legacy backends this implementation can read, lower-case
     * with no whitespace (e.g. {@code "essentialsx"}, {@code "democracytreasury"}).
     * The set must be non-empty and stable across the plugin's lifetime.
     */
    Set<String> supportedSources();

    /**
     * Run an ingest. Blocking — implementations may take seconds or minutes
     * for a large dataset. The dispatcher in Treasury invokes this off the
     * main server thread, so it is safe to perform JDBC work and disk I/O
     * here without further scheduling.
     *
     * <p>Implementations should periodically send progress lines to
     * {@code sender} (e.g. every N entries) so the operator can track
     * long-running runs. Bukkit's {@code CommandSender#sendMessage} is
     * thread-safe under Paper, so off-thread sends are permitted.
     *
     * @param source the value the operator typed; will be in
     *               {@link #supportedSources()}
     * @param sender command initiator (player or console)
     * @return summary of the run; never {@code null}
     * @throws RuntimeException if the run could not start (e.g. source
     *         directory missing, schema lookup failed). Per-row failures
     *         should be counted in {@link IngestReport#playersFailed()}
     *         rather than thrown.
     */
    IngestReport ingest(String source, CommandSender sender);
}
