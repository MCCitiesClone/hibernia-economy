package io.paradaux.jobs.model.config;


import java.util.Set;
import java.util.Collections;
import java.util.LinkedHashSet;

/**
 * One job, licence or qualification as written in {@code jobs.yml}.
 *
 * <p>The map key in the enclosing {@code jobs:} section is this job's key, and the
 * binder does not pass it into the value object — {@code JobRegistry} supplies it
 * when building its snapshot, which is also where the {@value #UNSET} sentinels are
 * resolved (an unset display name becomes a title-cased key, an unset group becomes
 * the key verbatim).</p>
 *
 * <p>The sentinel exists because the framework logs a warning for any absent scalar
 * whose default is empty; with a file of a dozen jobs that would be a warning storm
 * for perfectly valid configuration.</p>
 */
public record JobSettings(
        String displayName,
        String group,
        String description,
        Set<String> canManage,
        ProvisionSettings provision
) {

    public static final String UNSET = "<unset>";

    public JobSettings {
        canManage = canManage == null || canManage.isEmpty()
                ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(canManage));
    }

    public static boolean isUnset(String value) {
        return value == null || value.isBlank() || UNSET.equals(value);
    }
}
