package io.paradaux.jobs.model.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** Parser-level coverage, including a round-trip of the actual shipped file. */
class JobsYamlTest {

    private static JobsSettings parse(String yaml) {
        return JobsYaml.parse(YamlConfiguration.loadConfiguration(new StringReader(yaml)));
    }

    @Test
    void theShippedJobsYmlParses() throws Exception {
        // Guards the file operators actually receive: if it ever stops parsing, or a
        // type key is renamed without updating listing-commands, this fails here
        // rather than on someone's server.
        try (InputStream in = getClass().getResourceAsStream("/jobs.yml")) {
            assertThat(in).as("jobs.yml must be on the classpath").isNotNull();
            YamlConfiguration config = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            JobsSettings settings = JobsYaml.parse(config);

            assertThat(settings.adminPermission()).isEqualTo("jobs.admin");
            assertThat(settings.provisionGroups()).isTrue();
            assertThat(settings.types().keySet())
                    .containsExactlyInAnyOrder("government", "legal", "professions",
                            "trades", "licenses", "qualifications");
            // Trades arrive from the skills plugin, so hand-hiring is off by default.
            assertThat(settings.types().get("trades").managedExternally()).isTrue();
            assertThat(settings.types().get("government").managedExternally()).isFalse();
            // Every listing command must name a type that exists.
            settings.listingCommands().forEach((command, type) ->
                    assertThat(settings.types()).as("listing-commands." + command).containsKey(type));
        }
    }

    @Test
    void anEmptyDocumentYieldsUsableDefaults() {
        JobsSettings settings = parse("");
        assertThat(settings.adminPermission()).isEqualTo("jobs.admin");
        assertThat(settings.provisionGroups()).isTrue();
        assertThat(settings.showEmptyTypes()).isFalse();
        assertThat(settings.types()).isEmpty();
        assertThat(settings.reconciliation().enabled()).isTrue();
        assertThat(settings.reconciliation().intervalSeconds()).isEqualTo(1800L);
    }

    @Test
    void aNullRootYieldsDefaultsRatherThanThrowing() {
        assertThat(JobsYaml.parse(null).types()).isEmpty();
    }

    @Test
    void aNonPositiveReconciliationIntervalFallsBackToTheDefault() {
        // A 0 interval would schedule a task that never fires; silently disabling the
        // reconciler would be worse than ignoring the bad value.
        assertThat(parse("reconciliation:\n  interval-seconds: 0\n")
                .reconciliation().intervalSeconds()).isEqualTo(1800L);
        assertThat(parse("reconciliation:\n  interval-seconds: -5\n")
                .reconciliation().intervalSeconds()).isEqualTo(1800L);
    }

    @Test
    void nestedJobsAndTypesBind() {
        JobsSettings settings = parse("""
                types:
                  government:
                    display-name: "Government"
                    order: 10
                    can-manage: [ "licenses/*" ]
                    jobs:
                      president:
                        display-name: "President"
                        group: "president"
                        description: "Head of state."
                        can-manage: [ "government/*" ]
                """);
        JobTypeSettings type = settings.types().get("government");
        assertThat(type.displayName()).isEqualTo("Government");
        assertThat(type.order()).isEqualTo(10);
        assertThat(type.canManage()).containsExactly("licenses/*");

        JobSettings job = type.jobs().get("president");
        assertThat(job.group()).isEqualTo("president");
        assertThat(job.description()).isEqualTo("Head of state.");
        assertThat(job.canManage()).containsExactly("government/*");
    }

    @Test
    void omittedScalarsBecomeTheUnsetSentinelRatherThanNull() {
        JobSettings job = parse("""
                types:
                  a:
                    jobs:
                      b: {}
                """).types().get("a").jobs().get("b");
        assertThat(JobSettings.isUnset(job.displayName())).isTrue();
        assertThat(JobSettings.isUnset(job.group())).isTrue();
        assertThat(job.canManage()).isEmpty();
        assertThat(job.provision().isEmpty()).isTrue();
    }

    // ---- provision metadata ----

    @Test
    void jobProvisionOverridesTypeProvisionKeyByKey() {
        JobsSettings settings = parse("""
                types:
                  professions:
                    provision:
                      weight: 20
                      prefix: "[Professional] "
                      prefix-color: "<aqua>"
                      permissions: [ "jobs.profession" ]
                    jobs:
                      lawyer:
                        provision:
                          prefix: "[Lawyer] "
                          permissions: [ "jobs.lawyer" ]
                """);
        ProvisionSettings type = settings.types().get("professions").provision();
        ProvisionSettings job = settings.types().get("professions").jobs().get("lawyer").provision();
        ProvisionSettings merged = type.mergedWith(job);

        assertThat(merged.prefixValue()).contains("[Lawyer] ");          // overridden
        assertThat(merged.weightValue()).contains(20);                   // inherited
        assertThat(merged.prefixColorValue()).contains("<aqua>");        // inherited
        assertThat(merged.permissions())
                .containsExactly("jobs.profession", "jobs.lawyer");      // unioned, not replaced
        // The colour is prepended to the text the job supplied.
        assertThat(merged.resolvedPrefix()).contains("<aqua>[Lawyer] ");
    }

    @Test
    void mergingWithAnEmptyOverrideChangesNothing() {
        ProvisionSettings type = parse("""
                types:
                  a:
                    provision: { weight: 5 }
                    jobs:
                      b: {}
                """).types().get("a").provision();
        assertThat(type.mergedWith(ProvisionSettings.empty()).weightValue()).contains(5);
        assertThat(type.mergedWith(null).weightValue()).contains(5);
    }

    @Test
    void anUndeclaredKeyIsDistinguishableFromZero() {
        // The distinction matters: an undeclared weight must leave LuckPerms alone,
        // while a declared 0 must be applied as 0.
        ProvisionSettings undeclared = parse("types:\n  a:\n    provision: { prefix: x }\n    jobs: {}\n")
                .types().get("a").provision();
        assertThat(undeclared.weightValue()).isEmpty();

        ProvisionSettings zero = parse("types:\n  a:\n    provision: { weight: 0 }\n    jobs: {}\n")
                .types().get("a").provision();
        assertThat(zero.weightValue()).contains(0);
    }

    @Test
    void aNonNumericWeightIsFlaggedRatherThanApplied() {
        ProvisionSettings provision = parse("types:\n  a:\n    provision: { weight: heavy }\n    jobs: {}\n")
                .types().get("a").provision();
        assertThat(provision.weightValue()).isEmpty();
        assertThat(provision.hasMalformedWeight()).isTrue();
    }

    @Test
    void aColourWithNoPrefixTextResolvesToNothing() {
        ProvisionSettings provision = parse("""
                types:
                  a:
                    provision: { prefix-color: "<red>" }
                    jobs: {}
                """).types().get("a").provision();
        assertThat(provision.prefixColorValue()).contains("<red>");
        assertThat(provision.resolvedPrefix()).isEmpty();
    }
}
