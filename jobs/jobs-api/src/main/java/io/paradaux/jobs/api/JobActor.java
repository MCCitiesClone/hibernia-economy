package io.paradaux.jobs.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Who is performing a job change, and whether they bypass the can-manage hierarchy.
 *
 * <p>Authority is a property of the <em>actor</em>, never of the entry point. There
 * is one authority code path shared by {@code /hire}, {@code /fire} and
 * {@link JobsApi}, so a plugin that wants the hierarchy enforced — an in-game hiring
 * GUI, say — passes {@link #player} and gets behaviour identical to the command.
 * A plugin granting a trade it owns passes {@link #plugin} and bypasses. One knob,
 * explicit at every call site, and recorded in the audit log either way.</p>
 *
 * <p>Console, plugin and system actors are always privileged: they have no jobs of
 * their own, so there is no hierarchy that could meaningfully apply to them.</p>
 */
public record JobActor(@NotNull ActorType type,
                       @Nullable UUID uuid,
                       @Nullable String name,
                       boolean privileged) {

    public JobActor {
        if (type == null) {
            throw new IllegalArgumentException("Actor type must not be null");
        }
        if (type == ActorType.PLAYER && uuid == null) {
            throw new IllegalArgumentException("A PLAYER actor requires a uuid");
        }
        if (type == ActorType.PLUGIN && (name == null || name.isBlank())) {
            // Required so a misbehaving caller is identifiable in the audit log —
            // a privileged grant must never be anonymous.
            throw new IllegalArgumentException("A PLUGIN actor requires a plugin name");
        }
        if (type != ActorType.PLAYER) {
            privileged = true;
        }
    }

    /**
     * A player acting on their own authority.
     *
     * @param hasAdminPermission whether they hold the configured admin permission
     *                           ({@code jobs.admin} by default), which bypasses the
     *                           can-manage hierarchy entirely.
     */
    public static JobActor player(@NotNull UUID uuid, @Nullable String name, boolean hasAdminPermission) {
        return new JobActor(ActorType.PLAYER, uuid, name, hasAdminPermission);
    }

    /** The server console or RCON. Always privileged. */
    public static JobActor console() {
        return new JobActor(ActorType.CONSOLE, null, "CONSOLE", true);
    }

    /** Another plugin acting on its own behalf. Always privileged; the name is required. */
    public static JobActor plugin(@NotNull String pluginName) {
        return new JobActor(ActorType.PLUGIN, null, pluginName, true);
    }

    /** This plugin's reconciler, recording membership it discovered. Always privileged. */
    public static JobActor system() {
        return new JobActor(ActorType.SYSTEM, null, "SYSTEM", true);
    }

    public Optional<UUID> uuidOptional() {
        return Optional.ofNullable(uuid);
    }

    /** A short label for messages and logs — never null. */
    public @NotNull String displayName() {
        if (name != null && !name.isBlank()) {
            return name;
        }
        return uuid != null ? uuid.toString() : type.name();
    }
}
