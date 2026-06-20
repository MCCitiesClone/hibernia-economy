package io.paradaux.treasury.services;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.paradaux.hibernia.framework.configurator.ConfigurationLoader;
import io.paradaux.hibernia.framework.configurator.ConfigurationProcessor;
import io.paradaux.hibernia.framework.i18n.Message;
import lombok.extern.slf4j.Slf4j;
import io.paradaux.treasury.Treasury;
import io.paradaux.treasury.model.config.BalanceTaxConfiguration;
import io.paradaux.treasury.model.config.LoggingConfiguration;
import io.paradaux.treasury.model.config.SalaryConfiguration;
import io.paradaux.treasury.model.config.SourceIncomeTaxConfiguration;
import io.paradaux.treasury.utils.LoggingConfigurer;

/**
 * Refreshes the runtime configuration without a full plugin restart, for the
 * {@code /treasury reload} command.
 *
 * <p>What it reloads:
 * <ul>
 *   <li>{@code messages.properties} via {@link Message#reload()};</li>
 *   <li>the annotation-driven {@code @ConfigurationComponent} POJOs (Economy,
 *       Government, Logging, TaxCycle, Bytebin, DiscordWebhook, Database) — the
 *       same singleton instances services hold are re-populated in place by
 *       re-running the framework {@link ConfigurationProcessor} over them;</li>
 *   <li>the manually-parsed config POJOs (salaries, balance tax, source-income
 *       tax) via their own {@code reload()};</li>
 *   <li>the log level (re-applied via {@link LoggingConfigurer}).</li>
 * </ul>
 *
 * <p><b>Not</b> reloaded (constructed once per JVM — need a restart): the DB pool,
 * the Guice injector, the {@code account}-formatter pattern cached in
 * AccountService, and the salary/tax-cycle <em>schedule intervals</em> (the timers
 * are fixed when scheduled at enable; amounts/rates/enabled flags do update live).
 */
@Slf4j
@Singleton
public class ConfigReloadService {

    private final Treasury plugin;
    private final Message message;
    private final ConfigurationLoader configurationLoader;
    private final SalaryConfiguration salaryConfiguration;
    private final BalanceTaxConfiguration balanceTaxConfiguration;
    private final SourceIncomeTaxConfiguration sourceIncomeTaxConfiguration;
    private final LoggingConfiguration loggingConfiguration;

    @Inject
    public ConfigReloadService(Treasury plugin,
                               Message message,
                               ConfigurationLoader configurationLoader,
                               SalaryConfiguration salaryConfiguration,
                               BalanceTaxConfiguration balanceTaxConfiguration,
                               SourceIncomeTaxConfiguration sourceIncomeTaxConfiguration,
                               LoggingConfiguration loggingConfiguration) {
        this.plugin = plugin;
        this.message = message;
        this.configurationLoader = configurationLoader;
        this.salaryConfiguration = salaryConfiguration;
        this.balanceTaxConfiguration = balanceTaxConfiguration;
        this.sourceIncomeTaxConfiguration = sourceIncomeTaxConfiguration;
        this.loggingConfiguration = loggingConfiguration;
    }

    /** Re-reads config.yml + messages.properties and refreshes the live config objects. */
    public void reloadAll() {
        // 1) Re-read the files from disk.
        plugin.reloadConfig();
        message.reload();

        // 2) Re-populate the @ConfigurationComponent singletons in place (same
        //    instances injected into services), reading the now-fresh config.
        ConfigurationProcessor processor = new ConfigurationProcessor(plugin);
        configurationLoader.getComponents().values().forEach(processor::process);

        // 3) Refresh the manually-parsed config POJOs.
        salaryConfiguration.reload();
        balanceTaxConfiguration.reload();
        sourceIncomeTaxConfiguration.reload();

        // 4) Re-apply startup side effects that are cheap and safe to redo.
        LoggingConfigurer.apply(loggingConfiguration.getLevel());

        log.info("Configuration reloaded (config.yml + messages.properties).");
    }
}
