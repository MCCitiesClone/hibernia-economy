package io.paradaux.jobs.api.model;

import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * One configured job, licence or qualification.
 *
 * @param id          the {@code type/job} identity
 * @param displayName human-readable name, safe to rename at any time
 * @param group       the LuckPerms group that <em>is</em> this job's membership
 * @param description one-line blurb shown by {@code /jobs info}
 * @param canManage   selectors naming what a holder of this job may hire into and
 *                    fire from, already unioned with its type's selectors. Grammar
 *                    is {@code <type>/<job>} with {@code *} allowed on either side.
 */
public record JobDefinition(@NotNull JobId id,
                            @NotNull String displayName,
                            @NotNull String group,
                            @NotNull String description,
                            @NotNull Set<String> canManage) {

    public JobDefinition {
        canManage = canManage == null ? Set.of() : Set.copyOf(canManage);
    }
}
