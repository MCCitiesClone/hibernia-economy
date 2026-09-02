package io.paradaux.jobs.permissions;

import io.paradaux.jobs.model.config.ProvisionSettings;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Everything this plugin needs from a permission system, with no LuckPerms types in
 * the signatures.
 *
 * <p>Confining {@code net.luckperms.*} to a single implementation buys three things.
 * The class literal is never resolved when the plugin is absent, so there is no
 * {@code NoClassDefFoundError} path at all. Every service becomes unit-testable
 * against a mock, with no live permission plugin. And LuckPerms result types never
 * leak into the public API, so the two can version independently.</p>
 *
 * <p>This is a deliberate tightening of the {@code @Inject(optional = true)} idiom
 * Treasury uses for its read-only LuckPerms access: with a write surface this size,
 * a null check scattered through every service is worse than one binding decision
 * made once in the Guice module.</p>
 *
 * <p><b>Threading:</b> every method here may block on storage. Callers must be off
 * the main thread — command handlers are {@code @Async}, and the API returns futures.</p>
 */
public interface PermissionBackend {

    /** Whether a permission plugin is present and usable. */
    boolean available();

    /**
     * Groups assigned to the player directly. These are the only ones a fire or quit
     * can remove — a group inherited from a parent rank has no node on the user.
     */
    CompletableFuture<Set<String>> directGroups(UUID subject);

    /**
     * Groups the player effectively holds, including those inherited from parent
     * ranks. This is what a listing shows: hiding an inherited job would
     * misrepresent the player's real authority.
     */
    CompletableFuture<Set<String>> inheritedGroups(UUID subject);

    /**
     * Cache-only, synchronous, non-blocking membership read.
     *
     * <p>Returns empty for an offline or uncached player rather than loading them, so
     * it is only safe where a false negative is acceptable. Backs
     * {@code JobsApi.holdsCached}.</p>
     */
    Set<String> cachedInheritedGroups(UUID subject);

    /** Create {@code group} if it does not exist. Idempotent. */
    CompletableFuture<Void> ensureGroup(String group);

    /**
     * Apply the declared provisioning metadata to a group.
     *
     * <p>Only keys the configuration actually declares are written; anything
     * undeclared is left exactly as LuckPerms has it, so hand-tuned values survive.</p>
     *
     * @param color the job's resolved colour as a MiniMessage tag, written as group
     *              meta under {@value io.paradaux.jobs.permissions.LuckPermsBackend#COLOR_META_KEY}
     *              so other plugins and chat formats can read it. Blank writes nothing.
     */
    CompletableFuture<Void> applyMetadata(String group, ProvisionSettings provision, String color);

    /** Give the player the group. Works whether or not they are online. */
    CompletableFuture<MutationOutcome> addGroup(UUID subject, String group);

    /** Take the group from the player. Works whether or not they are online. */
    CompletableFuture<MutationOutcome> removeGroup(UUID subject, String group);

    /**
     * Everyone holding {@code group} as a direct node. Used by the reconciler, and
     * deliberately direct-only so it matches what the write path can affect.
     */
    CompletableFuture<Set<UUID>> directHolders(String group);
}
