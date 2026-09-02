package io.paradaux.jobs.services;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.paradaux.common.messaging.TagAwareMessage;
import io.paradaux.hibernia.framework.i18n.Message;
import io.paradaux.jobs.api.model.HeldJob;
import io.paradaux.jobs.api.model.JobType;
import io.paradaux.jobs.utils.JobColors;
import org.bukkit.command.CommandSender;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The single place a job listing is formatted.
 *
 * <p>{@code /jobs}, {@code /licenses} and {@code /qualifications} differ only in
 * which types they show, so they all call this with a different {@link Filter}. The
 * three command classes exist solely because the framework reads routes from
 * {@code getDeclaredMethods()} and {@code @Command} is a compile-time literal —
 * neither the roots nor their routes can be generated from configuration.</p>
 */
@Singleton
public final class JobListRenderer {

    /** Which types to show: everything, or exactly one. */
    public record Filter(String typeKey) {

        public static Filter all() {
            return new Filter(null);
        }

        public static Filter only(String typeKey) {
            return new Filter(typeKey);
        }

        public boolean isAll() {
            return typeKey == null;
        }
    }

    private final Message message;
    private final JobService jobs;
    private final JobRegistry registry;

    @Inject
    public JobListRenderer(Message message, JobService jobs, JobRegistry registry) {
        this.message = message;
        this.jobs = jobs;
        this.registry = registry;
    }

    /**
     * Render {@code subject}'s jobs to {@code to}.
     *
     * <p>Called from an {@code @Async} route, so the membership read blocks here
     * rather than on the main thread.</p>
     */
    public void render(CommandSender to, java.util.UUID subject, String subjectName, Filter filter) {
        JobSnapshot snapshot = registry.snapshot();

        if (!filter.isAll() && snapshot.type(filter.typeKey()).isEmpty()) {
            message.send(to, "jobs.list.type-unconfigured");
            return;
        }

        List<HeldJob> held = filter.isAll()
                ? jobs.heldJobs(subject).join()
                : jobs.heldJobsOfType(subject, filter.typeKey()).join();

        String typeLabel = filter.isAll() ? null : snapshot.type(filter.typeKey())
                .map(type -> type.displayName()).orElse(filter.typeKey());

        if (held.isEmpty() && !snapshot.showEmptyTypes()) {
            if (filter.isAll()) {
                message.send(to, "jobs.list.empty", "target", subjectName);
            } else {
                message.send(to, "jobs.list.type-empty", "target", subjectName, "type", typeLabel);
            }
            return;
        }

        message.send(to, "jobs.list.header", "target", subjectName);

        // Group by type, preserving the configured section order that heldJobs
        // already applied.
        Map<String, List<HeldJob>> byType = new LinkedHashMap<>();
        held.forEach(job -> byType.computeIfAbsent(job.typeKey(), k -> new java.util.ArrayList<>()).add(job));

        for (var type : snapshot.types()) {
            if (!filter.isAll() && !type.key().equalsIgnoreCase(filter.typeKey())) {
                continue;
            }
            List<HeldJob> inType = byType.get(type.key());
            if (inType == null || inType.isEmpty()) {
                if (!snapshot.showEmptyTypes()) {
                    continue;
                }
                sendSectionHeader(to, type);
                message.send(to, "jobs.list.section-empty");
                continue;
            }
            sendSectionHeader(to, type);
            for (HeldJob job : inType) {
                renderEntry(to, job);
            }
        }
    }

    /**
     * Emit a section header in the type's configured colour.
     *
     * <p>The colour is passed as {@link Message#rich} because it is a MiniMessage tag
     * from operator-authored configuration, not player input — an inert placeholder
     * would print the tag rather than apply it. An unconfigured type falls back to
     * the message bundle's own palette, so the header is never left uncoloured.</p>
     */
    private void sendSectionHeader(CommandSender to, JobType type) {
        String open = type.hasColor() ? type.color() : "{secbegin}";
        String close = type.hasColor() ? JobColors.closing(type.color()) : "{secend}";
        message.send(to, "jobs.list.section",
                "type", type.displayName(),
                "color", Message.rich(open),
                "colorend", Message.rich(close));
    }

    private void renderEntry(CommandSender to, HeldJob job) {
        if (!job.direct()) {
            // Shown, but marked: it is real authority, yet /fire and /quit cannot
            // remove it because there is no direct node to remove.
            message.send(to, "jobs.list.entry-inherited", "name", job.displayName());
            return;
        }
        // The entry embeds {job} inside a <click:run_command:'...'> argument, which
        // plain Message.send cannot resolve — a tag argument is a literal string, so
        // the generated <phN> tag would reach the client verbatim.
        TagAwareMessage.send(message, to, "jobs.list.entry",
                "name", job.displayName(), "job", job.id().qualified());
    }

    /** The type key a dedicated listing command shows, or empty when unconfigured. */
    public Optional<String> listingType(String command) {
        return registry.snapshot().listingType(command);
    }
}
