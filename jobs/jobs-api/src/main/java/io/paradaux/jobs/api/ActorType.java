package io.paradaux.jobs.api;

/** Who initiated a job change. Recorded on every audit row. */
public enum ActorType {
    /** A player, via a command or a plugin acting explicitly on their behalf. */
    PLAYER,
    /** The server console or RCON. */
    CONSOLE,
    /** Another plugin calling {@link JobsApi} — for example the trades plugin. */
    PLUGIN,
    /** This plugin's own reconciler, recording membership it discovered. */
    SYSTEM
}
