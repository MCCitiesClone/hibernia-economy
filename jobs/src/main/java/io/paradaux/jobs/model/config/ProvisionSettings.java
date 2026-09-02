package io.paradaux.jobs.model.config;


import java.util.List;
import java.util.Optional;

/**
 * LuckPerms metadata to apply to a job's group when it is provisioned.
 *
 * <p>May be declared on a type (inherited by every job in it) and on an individual
 * job, which overrides the type <em>key by key</em> — so a type can set a shared
 * colour while each job supplies its own prefix text.</p>
 *
 * <h2>Undeclared vs. declared</h2>
 * <p>The plugin applies <strong>only the keys the configuration actually declares</strong>
 * and never touches anything else on the group, so a weight set by hand in LuckPerms
 * survives if no weight is configured here. The corollary: a key that <em>is</em>
 * declared is reasserted on every reload, so hand-edits to it will be overwritten.</p>
 *
 * <p>"Undeclared" must stay distinguishable from "declared as empty", which a plain
 * nullable {@code String} would blur once a partially-filled block is merged with
 * its parent. Hence the {@value #UNSET} sentinel, and hence reading through the
 * {@code Optional} accessors rather than the raw record components.</p>
 */
public record ProvisionSettings(
        String weight,
        String prefix,
        String prefixColor,
        List<String> permissions
) {

    /** Marks a scalar the operator did not declare. Implausible as a real value. */
    public static final String UNSET = "<unset>";

    public ProvisionSettings {
        weight = blankToUnset(weight);
        prefix = blankToUnset(prefix);
        prefixColor = blankToUnset(prefixColor);
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }

    /** An empty block: nothing declared, so nothing is ever applied. */
    public static ProvisionSettings empty() {
        return new ProvisionSettings(UNSET, UNSET, UNSET, List.of());
    }

    /**
     * The group weight, if declared. A non-numeric value is treated as undeclared —
     * the registry logs it at build time rather than failing the whole config.
     */
    public Optional<Integer> weightValue() {
        if (isUnset(weight)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(weight.trim()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** True when {@code weight} was declared but is not a number — a config error worth reporting. */
    public boolean hasMalformedWeight() {
        return !isUnset(weight) && weightValue().isEmpty();
    }

    public Optional<String> prefixValue() {
        return isUnset(prefix) ? Optional.empty() : Optional.of(prefix);
    }

    /** A MiniMessage colour tag (e.g. {@code <aqua>}) prepended to the prefix text. */
    public Optional<String> prefixColorValue() {
        return isUnset(prefixColor) ? Optional.empty() : Optional.of(prefixColor);
    }

    /** Whether this block declares nothing at all. */
    public boolean isEmpty() {
        return isUnset(weight) && isUnset(prefix) && isUnset(prefixColor) && permissions.isEmpty();
    }

    /**
     * Overlay {@code override} onto this block key by key: any key the override
     * declares wins, and anything it leaves undeclared falls through. Permissions are
     * unioned rather than replaced, so a job adds to its type's permissions.
     */
    public ProvisionSettings mergedWith(ProvisionSettings override) {
        if (override == null || override.isEmpty()) {
            return this;
        }
        List<String> merged = new java.util.ArrayList<>(permissions);
        for (String permission : override.permissions()) {
            if (!merged.contains(permission)) {
                merged.add(permission);
            }
        }
        return new ProvisionSettings(
                isUnset(override.weight) ? weight : override.weight,
                isUnset(override.prefix) ? prefix : override.prefix,
                isUnset(override.prefixColor) ? prefixColor : override.prefixColor,
                merged);
    }

    /**
     * The prefix to write to LuckPerms: the colour tag, if any, prepended to the
     * text. A colour with no text is meaningless and yields nothing.
     */
    public Optional<String> resolvedPrefix() {
        return prefixValue().map(text -> prefixColorValue().map(colour -> colour + text).orElse(text));
    }

    private static boolean isUnset(String value) {
        return value == null || value.isBlank() || UNSET.equals(value);
    }

    private static String blankToUnset(String value) {
        return value == null || value.isBlank() ? UNSET : value;
    }
}
