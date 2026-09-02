package io.paradaux.jobs.permissions;

/** A permission-backend operation failed or timed out. */
public class BackendException extends RuntimeException {

    public BackendException(String message) {
        super(message);
    }

    public BackendException(String message, Throwable cause) {
        super(message, cause);
    }

    public static BackendException unavailable() {
        return new BackendException("LuckPerms is not available");
    }
}
