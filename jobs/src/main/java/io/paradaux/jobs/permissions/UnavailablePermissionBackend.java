package io.paradaux.jobs.permissions;

import com.google.inject.Singleton;
import io.paradaux.jobs.model.config.ProvisionSettings;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The backend bound when LuckPerms is absent.
 *
 * <p>Reads return empty and writes fail, so the plugin still enables and every
 * command answers with a clear "LuckPerms is unavailable" rather than a stack trace.
 * Contains no reference to {@code net.luckperms.*}, which is the whole point — with
 * this bound, nothing on any code path can trigger a {@code NoClassDefFoundError}.</p>
 */
@Singleton
public final class UnavailablePermissionBackend implements PermissionBackend {

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public CompletableFuture<Set<String>> directGroups(UUID subject) {
        return CompletableFuture.completedFuture(Set.of());
    }

    @Override
    public CompletableFuture<Set<String>> inheritedGroups(UUID subject) {
        return CompletableFuture.completedFuture(Set.of());
    }

    @Override
    public Set<String> cachedInheritedGroups(UUID subject) {
        return Set.of();
    }

    @Override
    public CompletableFuture<Void> ensureGroup(String group) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> applyMetadata(String group, ProvisionSettings provision,
                                                 String color) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<MutationOutcome> addGroup(UUID subject, String group) {
        return CompletableFuture.completedFuture(MutationOutcome.FAILED);
    }

    @Override
    public CompletableFuture<MutationOutcome> removeGroup(UUID subject, String group) {
        return CompletableFuture.completedFuture(MutationOutcome.FAILED);
    }

    @Override
    public CompletableFuture<Set<UUID>> directHolders(String group) {
        return CompletableFuture.completedFuture(Set.of());
    }
}
