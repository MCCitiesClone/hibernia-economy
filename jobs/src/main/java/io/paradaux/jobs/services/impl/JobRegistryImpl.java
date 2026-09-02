package io.paradaux.jobs.services.impl;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.paradaux.jobs.model.config.JobsSettings;
import io.paradaux.jobs.model.config.JobsYaml;
import io.paradaux.jobs.services.JobRegistry;
import io.paradaux.jobs.services.JobSnapshot;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

/**
 * Owns {@code jobs.yml} and publishes the derived {@link JobSnapshot}.
 *
 * <p>This is the only class that reads the file or references {@link JobsSettings},
 * which is what keeps reloads correct: {@link #rebuild()} re-reads from disk and
 * swaps a fully-built immutable snapshot into an {@link AtomicReference}, so readers
 * see either the whole old configuration or the whole new one, never a mixture. Every
 * other class depends on {@link JobRegistry} and therefore always sees current
 * configuration without having to think about it.</p>
 *
 * <p>The file is copied out of the jar on first run and never overwritten
 * afterwards. There is deliberately no merge of new jar defaults into an operator's
 * file: {@code jobs.yml} <em>is</em> the operator's dataset, so an additive merge
 * would resurrect jobs they had deliberately deleted. Shipped defaults therefore
 * declare types only, with the worked example left commented out.</p>
 */
@Singleton
public final class JobRegistryImpl implements JobRegistry {

    private static final String FILE_NAME = "jobs.yml";

    private final File file;
    private final Logger log;
    private final AtomicReference<JobSnapshot> snapshot = new AtomicReference<>(JobSnapshot.empty());

    @Inject
    public JobRegistryImpl(JavaPlugin plugin) {
        this(resolveFile(plugin), plugin.getLogger());
    }

    /** Test seam: point the registry at an arbitrary file with no Bukkit plugin. */
    public JobRegistryImpl(File file, Logger log) {
        this.file = file;
        this.log = log;
        rebuild();
    }

    private static File resolveFile(JavaPlugin plugin) {
        File target = new File(plugin.getDataFolder(), FILE_NAME);
        if (!target.exists()) {
            // replace=false: never clobber an operator's edited file.
            plugin.saveResource(FILE_NAME, false);
        }
        return target;
    }

    @Override
    public JobSnapshot snapshot() {
        return snapshot.get();
    }

    @Override
    public void rebuild() {
        try {
            if (file == null || !file.exists()) {
                log.warning(FILE_NAME + " not found; no jobs are configured.");
                snapshot.set(JobSnapshot.empty());
                return;
            }
            JobsSettings settings = JobsYaml.parse(YamlConfiguration.loadConfiguration(file));
            snapshot.set(JobSnapshot.build(settings, log));
        } catch (RuntimeException e) {
            // Keep serving the previous snapshot rather than dropping every job
            // because one reload hit a malformed file.
            log.severe("Failed to reload " + FILE_NAME
                    + "; keeping the previous configuration: " + e.getMessage());
        }
    }
}
