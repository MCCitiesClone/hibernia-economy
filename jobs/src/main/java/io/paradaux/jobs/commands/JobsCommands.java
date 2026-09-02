package io.paradaux.jobs.commands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.paradaux.hibernia.framework.commander.annotations.Arg;
import io.paradaux.hibernia.framework.commander.annotations.Async;
import io.paradaux.hibernia.framework.commander.annotations.Command;
import io.paradaux.hibernia.framework.commander.annotations.Description;
import io.paradaux.hibernia.framework.commander.annotations.GreedyArg;
import io.paradaux.hibernia.framework.commander.annotations.OptionalArg;
import io.paradaux.hibernia.framework.commander.annotations.Permission;
import io.paradaux.hibernia.framework.commander.annotations.Route;
import io.paradaux.hibernia.framework.commander.annotations.Sender;
import io.paradaux.hibernia.framework.commander.spi.CommandHandler;
import io.paradaux.hibernia.framework.i18n.Message;
import io.paradaux.jobs.api.model.JobDefinition;
import io.paradaux.jobs.api.model.JobId;
import io.paradaux.jobs.commands.resolvers.JobArg;
import io.paradaux.jobs.commands.resolvers.JobTypeArg;
import io.paradaux.jobs.model.JobEventRow;
import io.paradaux.jobs.model.JobMembershipRow;
import io.paradaux.jobs.services.GroupProvisioner;
import io.paradaux.jobs.services.JobAuditService;
import io.paradaux.jobs.services.JobListRenderer;
import io.paradaux.jobs.services.JobRegistry;
import io.paradaux.jobs.services.JobSnapshot;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * The {@code /jobs} root: the canonical form of every job command.
 *
 * <p>{@code /jobs hire|fire|quit} always work. The bare {@code /hire}, {@code /fire}
 * and {@code /quit} roots are conveniences that may lose their name to another plugin
 * — generic names, first registration wins — so the canonical forms live here.</p>
 */
@Singleton
@Command({"jobs", "job"})
public final class JobsCommands implements CommandHandler {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MM/dd HH:mm").withZone(ZoneId.systemDefault());

    private static final int DEFAULT_HISTORY_LIMIT = 10;
    private static final int MAX_HISTORY_LIMIT = 50;

    private final Message message;
    private final JobActions actions;
    private final JobRegistry registry;
    private final JobAuditService audit;
    private final GroupProvisioner provisioner;
    private final ListingCommandRegistrar listingCommands;

    @Inject
    public JobsCommands(Message message, JobActions actions, JobRegistry registry,
                        JobAuditService audit, GroupProvisioner provisioner,
                        ListingCommandRegistrar listingCommands) {
        this.message = message;
        this.actions = actions;
        this.registry = registry;
        this.audit = audit;
        this.provisioner = provisioner;
        this.listingCommands = listingCommands;
    }

    // ---- listing ----

    @Route("")
    @Permission("jobs.use")
    @Async
    @Description("List the jobs, licences and qualifications you hold")
    public void self(@Sender CommandSender sender) {
        actions.listSelf(sender, JobListRenderer.Filter.all());
    }

    @Route("<player>")
    @Permission("jobs.list.other")
    @Async
    @Description("List the jobs another player holds")
    public void other(@Sender CommandSender sender, @Arg("player") OfflinePlayer target) {
        actions.listOther(sender, target, JobListRenderer.Filter.all());
    }

    /**
     * The config-driven per-type view. A brand-new type added to {@code jobs.yml}
     * becomes reachable here immediately, with no code change — which is the
     * mitigation for top-level command roots being compile-time literals.
     */
    @Route("type <type>")
    @Permission("jobs.use")
    @Async
    @Description("List the jobs you hold of one type")
    public void byType(@Sender CommandSender sender, @Arg("type") JobTypeArg type) {
        actions.listByType(sender, type.value(), null);
    }

    @Route("type <type> <player>")
    @Permission("jobs.list.other")
    @Async
    @Description("List the jobs another player holds of one type")
    public void byTypeOther(@Sender CommandSender sender, @Arg("type") JobTypeArg type,
                            @Arg("player") OfflinePlayer target) {
        actions.listByType(sender, type.value(), target);
    }

    // No hardcoded `licenses` / `qualifications` subcommands: jobs.yml decides which
    // listing commands exist, ListingCommandRegistrar creates them as top-level roots,
    // and `/jobs type <type>` already reaches every configured type generically.

    // ---- canonical mutations ----

    @Route("hire <player> <job>")
    @Permission("jobs.hire")
    @Async
    @Description("Hire a player into a job, licence or qualification")
    public void hire(@Sender CommandSender sender, @Arg("player") OfflinePlayer target,
                     @Arg("job") JobArg job) {
        actions.hire(sender, target, job, null);
    }

    @Route("hire <player> <job> <reason>")
    @Permission("jobs.hire")
    @Async
    @Description("Hire a player into a job, recording a reason")
    public void hireWithReason(@Sender CommandSender sender, @Arg("player") OfflinePlayer target,
                               @Arg("job") JobArg job, @GreedyArg("reason") String reason) {
        actions.hire(sender, target, job, reason);
    }

    @Route("fire <player> <job>")
    @Permission("jobs.fire")
    @Async
    @Description("Remove a player from a job, licence or qualification")
    public void fire(@Sender CommandSender sender, @Arg("player") OfflinePlayer target,
                     @Arg("job") JobArg job) {
        actions.fire(sender, target, job, null);
    }

    @Route("fire <player> <job> <reason>")
    @Permission("jobs.fire")
    @Async
    @Description("Remove a player from a job, recording a reason")
    public void fireWithReason(@Sender CommandSender sender, @Arg("player") OfflinePlayer target,
                               @Arg("job") JobArg job, @GreedyArg("reason") String reason) {
        actions.fire(sender, target, job, reason);
    }

    @Route("quit <job>")
    @Permission("jobs.quit")
    @Async
    @Description("Leave a job you hold")
    public void quit(@Sender CommandSender sender, @Arg("job") JobArg job) {
        actions.quit(sender, job);
    }

    // ---- information ----

    @Route("info <job>")
    @Permission("jobs.use")
    @Async
    @Description("Show what a job is and who may hire into it")
    public void info(@Sender CommandSender sender, @Arg("job") JobArg job) {
        JobSnapshot snapshot = registry.snapshot();
        Optional<JobId> id = CommandSupport.resolveJob(message, sender, snapshot, job.value());
        if (id.isEmpty()) {
            return;
        }
        JobDefinition definition = snapshot.job(id.get()).orElseThrow();
        message.send(sender, "jobs.info.header", "name", definition.displayName(),
                "job", id.get().qualified());
        message.send(sender, "jobs.info.detail",
                "description", definition.description().isEmpty() ? "-" : definition.description(),
                "group", definition.group());
        message.send(sender, "jobs.info.manages",
                "selectors", definition.canManage().isEmpty()
                        ? "-" : String.join(", ", definition.canManage()));
    }

    // ---- administration ----

    @Route("history <player>")
    @Permission("jobs.admin.audit")
    @Async
    @Description("Show a player's recent job history")
    public void history(@Sender CommandSender sender, @Arg("player") OfflinePlayer target) {
        historyLimited(sender, target, DEFAULT_HISTORY_LIMIT);
    }

    @Route("history <player> <limit>")
    @Permission("jobs.admin.audit")
    @Async
    @Description("Show a player's recent job history, capped at a limit")
    public void historyWithLimit(@Sender CommandSender sender, @Arg("player") OfflinePlayer target,
                                 @Arg("limit") int limit) {
        historyLimited(sender, target, limit);
    }

    private void historyLimited(CommandSender sender, OfflinePlayer target, int limit) {
        int capped = Math.clamp(limit, 1, MAX_HISTORY_LIMIT);
        List<JobEventRow> rows = audit.history(target.getUniqueId(), capped);
        String name = target.getName() != null ? target.getName() : target.getUniqueId().toString();
        if (rows.isEmpty()) {
            message.send(sender, "jobs.history.empty", "target", name);
            return;
        }
        message.send(sender, "jobs.history.header", "target", name);
        for (JobEventRow row : rows) {
            message.send(sender, "jobs.history.entry",
                    "date", row.getCreatedAt() == null ? "-"
                            : DATE_FMT.format(row.getCreatedAt().toInstant()),
                    "action", row.getAction(),
                    "job", row.getTypeKey() + "/" + row.getJobKey(),
                    "actor", actorLabel(row));
        }
    }

    /**
     * Memberships this plugin did not grant.
     *
     * <p>These are groups another plugin or an operator applied directly. They are
     * recorded rather than stripped, and this is how you find them — each one is a
     * candidate for moving its owner onto the JobsApi, which would produce proper
     * audit rows instead.</p>
     */
    @Route("audit external")
    @Permission("jobs.admin.audit")
    @Async
    @Description("List job memberships granted outside this plugin")
    public void auditExternal(@Sender CommandSender sender) {
        List<JobMembershipRow> rows = audit.externalMemberships(MAX_HISTORY_LIMIT);
        if (rows.isEmpty()) {
            message.send(sender, "jobs.audit.external.empty");
            return;
        }
        message.send(sender, "jobs.audit.external.header", "count",
                String.valueOf(audit.externalCount()));
        for (JobMembershipRow row : rows) {
            message.send(sender, "jobs.audit.external.entry",
                    "player", row.getSubjectUuid(),
                    "job", row.getTypeKey() + "/" + row.getJobKey(),
                    "group", row.getGroupName());
        }
    }

    @Route("reload")
    @Permission("jobs.admin.reload")
    @Async
    @Description("Reload jobs.yml and the message bundle")
    public void reload(@Sender CommandSender sender) {
        try {
            message.reload();
            registry.rebuild();
            // A job added to jobs.yml should work immediately, without a restart —
            // and so should a listing command added, renamed or removed there.
            listingCommands.registerAll();
            provisioner.provisionAll();
            message.send(sender, "jobs.admin.reload.success");
        } catch (RuntimeException e) {
            message.send(sender, "jobs.admin.reload.failed", "error", String.valueOf(e.getMessage()));
        }
    }

    private static String actorLabel(JobEventRow row) {
        if (row.getActorName() != null && !row.getActorName().isBlank()) {
            return row.getActorName();
        }
        return row.getActorUuid() != null ? row.getActorUuid() : row.getActorType();
    }
}
