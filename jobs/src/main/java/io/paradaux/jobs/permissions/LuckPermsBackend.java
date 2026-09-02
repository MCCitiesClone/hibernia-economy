package io.paradaux.jobs.permissions;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.paradaux.jobs.model.config.ProvisionSettings;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.data.DataMutateResult;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.matcher.NodeMatcher;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.PermissionNode;
import net.luckperms.api.node.types.PrefixNode;
import net.luckperms.api.node.types.WeightNode;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * The only class in this plugin that touches the LuckPerms API.
 *
 * <p>It is bound only when LuckPerms is present (see {@code JobsModule}'s
 * {@code Class.forName} guard), so the types below are never resolved on a server
 * without it.</p>
 *
 * <h2>Why {@code modifyUser}, not {@code getUser}</h2>
 * <p>Every write goes through {@code UserManager.modifyUser}, which loads the user,
 * applies the mutation and saves in one call — and, crucially, <strong>works for
 * offline players</strong>. The cache-only {@code getUser(uuid)} that Treasury's
 * services use returns null for anyone not currently loaded, which is fine for
 * payroll over online players but would silently fail to hire an offline one. The
 * cache-only path survives here in exactly one place,
 * {@link #cachedInheritedGroups(UUID)}, whose contract documents the false
 * negatives.</p>
 */
@Singleton
public final class LuckPermsBackend implements PermissionBackend {

    /** Priority for the provisioned prefix node. Low, so hand-set prefixes can outrank it. */
    private static final int PREFIX_PRIORITY = 10;

    private final LuckPerms luckPerms;

    @Inject
    public LuckPermsBackend(LuckPerms luckPerms) {
        this.luckPerms = luckPerms;
    }

    @Override
    public boolean available() {
        return true;
    }

    // ---- reads ----

    @Override
    public CompletableFuture<Set<String>> directGroups(UUID subject) {
        return luckPerms.getUserManager().loadUser(subject)
                .thenApply(user -> user.getNodes(NodeType.INHERITANCE).stream()
                        .map(InheritanceNode::getGroupName)
                        .collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    @Override
    public CompletableFuture<Set<String>> inheritedGroups(UUID subject) {
        return luckPerms.getUserManager().loadUser(subject)
                .thenApply(LuckPermsBackend::inheritedGroupNames);
    }

    @Override
    public Set<String> cachedInheritedGroups(UUID subject) {
        User user = luckPerms.getUserManager().getUser(subject);
        return user == null ? Set.of() : inheritedGroupNames(user);
    }

    private static Set<String> inheritedGroupNames(User user) {
        return user.getInheritedGroups(user.getQueryOptions()).stream()
                .map(Group::getName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public CompletableFuture<Set<UUID>> directHolders(String group) {
        NodeMatcher<InheritanceNode> matcher =
                NodeMatcher.key(InheritanceNode.builder(group).build());
        return luckPerms.getUserManager().searchAll(matcher)
                .thenApply(results -> new LinkedHashSet<>(results.keySet()));
    }

    // ---- writes ----

    @Override
    public CompletableFuture<MutationOutcome> addGroup(UUID subject, String group) {
        AtomicReference<MutationOutcome> outcome = new AtomicReference<>(MutationOutcome.FAILED);
        return luckPerms.getUserManager()
                .modifyUser(subject, user ->
                        outcome.set(map(user.data().add(InheritanceNode.builder(group).build()))))
                .handle((ignored, error) -> error != null ? MutationOutcome.FAILED : outcome.get());
    }

    @Override
    public CompletableFuture<MutationOutcome> removeGroup(UUID subject, String group) {
        AtomicReference<MutationOutcome> outcome = new AtomicReference<>(MutationOutcome.FAILED);
        return luckPerms.getUserManager()
                .modifyUser(subject, user ->
                        outcome.set(map(user.data().remove(InheritanceNode.builder(group).build()))))
                .handle((ignored, error) -> error != null ? MutationOutcome.FAILED : outcome.get());
    }

    @Override
    public CompletableFuture<Void> ensureGroup(String group) {
        // A cache hit is the overwhelmingly common case once startup provisioning has
        // run, so check it before paying for a storage round-trip.
        if (luckPerms.getGroupManager().getGroup(group) != null) {
            return CompletableFuture.completedFuture(null);
        }
        return luckPerms.getGroupManager().loadGroup(group)
                .thenCompose(existing -> existing.isPresent()
                        ? CompletableFuture.<Void>completedFuture(null)
                        : luckPerms.getGroupManager().createAndLoadGroup(group).thenApply(g -> null));
    }

    @Override
    public CompletableFuture<Void> applyMetadata(String group, ProvisionSettings provision) {
        if (provision == null || provision.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return luckPerms.getGroupManager().modifyGroup(group, target -> {
            // Only declared keys are written. Undeclared metadata is left untouched,
            // so a weight or prefix set by hand in LuckPerms survives a reload.
            provision.weightValue().ifPresent(weight -> {
                target.data().clear(NodeType.WEIGHT::matches);
                target.data().add(WeightNode.builder(weight).build());
            });
            provision.resolvedPrefix().ifPresent(prefix -> {
                target.data().clear(node -> node instanceof PrefixNode prefixNode
                        && prefixNode.getPriority() == PREFIX_PRIORITY);
                target.data().add(PrefixNode.builder(prefix, PREFIX_PRIORITY).build());
            });
            // Permissions are additive: the config declares what the group must have,
            // never what it must not, so an operator's extra grants are safe.
            provision.permissions().forEach(permission ->
                    target.data().add(PermissionNode.builder(permission).build()));
        });
    }

    private static MutationOutcome map(DataMutateResult result) {
        if (result.wasSuccessful()) {
            return MutationOutcome.CHANGED;
        }
        if (result == DataMutateResult.FAIL_ALREADY_HAS) {
            return MutationOutcome.ALREADY_HAD;
        }
        if (result == DataMutateResult.FAIL_LACKS) {
            return MutationOutcome.DID_NOT_HAVE;
        }
        return MutationOutcome.FAILED;
    }
}
