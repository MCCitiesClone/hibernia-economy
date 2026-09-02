package io.paradaux.jobs.services;

import io.paradaux.jobs.model.config.JobsSettings;
import io.paradaux.jobs.model.config.JobsYaml;
import io.paradaux.jobs.model.config.ProvisionSettings;
import io.paradaux.jobs.permissions.PermissionBackend;
import io.paradaux.jobs.permissions.UnavailablePermissionBackend;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.StringReader;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GroupProvisionerTest {

    @Mock private PermissionBackend backend;

    private final Logger log = Logger.getLogger("GroupProvisionerTest");

    private static final String CONFIG = """
            provision-groups: true
            types:
              professions:
                provision:
                  weight: 20
                  prefix-color: "<aqua>"
                jobs:
                  lawyer:
                    group: "lawyer"
                    provision:
                      prefix: "[Lawyer] "
                  doctor:
                    group: "doctor"
            """;

    private JobRegistry registryFor(String yaml) {
        JobsSettings settings = JobsYaml.parse(YamlConfiguration.loadConfiguration(new StringReader(yaml)));
        JobSnapshot snapshot = JobSnapshot.build(settings, log);
        return new JobRegistry() {
            @Override public JobSnapshot snapshot() { return snapshot; }
            @Override public void rebuild() { }
        };
    }

    @BeforeEach
    void setUp() {
        when(backend.available()).thenReturn(true);
        when(backend.ensureGroup(anyString())).thenReturn(CompletableFuture.completedFuture(null));
        when(backend.applyMetadata(anyString(), any(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    void everyConfiguredGroupIsCreated() {
        GroupProvisioner provisioner = new GroupProvisioner(registryFor(CONFIG), backend, log);

        GroupProvisioner.ProvisionReport report = provisioner.provisionAll();

        assertThat(report.considered()).isEqualTo(2);
        assertThat(report.succeeded()).isEqualTo(2);
        assertThat(report.clean()).isTrue();
        verify(backend).ensureGroup("lawyer");
        verify(backend).ensureGroup("doctor");
    }

    @Test
    void metadataIsAppliedWithTypeValuesInheritedAndJobValuesOverriding() {
        GroupProvisioner provisioner = new GroupProvisioner(registryFor(CONFIG), backend, log);

        provisioner.provisionAll();

        ArgumentCaptor<ProvisionSettings> applied = ArgumentCaptor.forClass(ProvisionSettings.class);
        verify(backend).applyMetadata(eq("lawyer"), applied.capture(), anyString());
        ProvisionSettings lawyer = applied.getValue();
        assertThat(lawyer.weightValue()).contains(20);                    // from the type
        assertThat(lawyer.resolvedPrefix()).contains("<aqua>[Lawyer] ");  // colour + job's text

        ArgumentCaptor<ProvisionSettings> doctorApplied = ArgumentCaptor.forClass(ProvisionSettings.class);
        verify(backend).applyMetadata(eq("doctor"), doctorApplied.capture(), anyString());
        // The doctor declares no prefix text, so a colour alone resolves to nothing.
        assertThat(doctorApplied.getValue().resolvedPrefix()).isEmpty();
        assertThat(doctorApplied.getValue().weightValue()).contains(20);
    }

    @Test
    void provisioningIsSkippedWhenDisabledInConfig() {
        GroupProvisioner provisioner = new GroupProvisioner(
                registryFor("provision-groups: false\ntypes:\n  a:\n    jobs:\n      b: {}\n"),
                backend, log);

        assertThat(provisioner.provisionAll().considered()).isZero();
        verify(backend, never()).ensureGroup(anyString());
    }

    @Test
    void provisioningIsSkippedAndWarnedWhenLuckPermsIsAbsent() {
        GroupProvisioner provisioner = new GroupProvisioner(
                registryFor(CONFIG), new UnavailablePermissionBackend(), log);

        // The plugin still enables; it simply creates nothing.
        assertThat(provisioner.provisionAll().considered()).isZero();
    }

    @Test
    void oneFailingGroupDoesNotAbortTheRest() {
        when(backend.ensureGroup("lawyer"))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("storage down")));
        GroupProvisioner provisioner = new GroupProvisioner(registryFor(CONFIG), backend, log);

        GroupProvisioner.ProvisionReport report = provisioner.provisionAll();

        assertThat(report.considered()).isEqualTo(2);
        assertThat(report.succeeded()).isEqualTo(1);
        assertThat(report.failed()).isEqualTo(1);
        assertThat(report.clean()).isFalse();
        verify(backend).ensureGroup("doctor");   // still attempted
    }

    @Test
    void ensureResolvesTheGroupForAKnownJob() {
        GroupProvisioner provisioner = new GroupProvisioner(registryFor(CONFIG), backend, log);

        provisioner.ensure(io.paradaux.jobs.api.model.JobId.of("professions", "lawyer")).join();

        verify(backend).ensureGroup("lawyer");
    }

    @Test
    void ensureIsANoOpForAnUnknownJobOrAnAbsentBackend() {
        GroupProvisioner provisioner = new GroupProvisioner(registryFor(CONFIG), backend, log);
        provisioner.ensure(io.paradaux.jobs.api.model.JobId.of("nope", "nope")).join();
        verify(backend, never()).ensureGroup("nope");

        GroupProvisioner offline = new GroupProvisioner(
                registryFor(CONFIG), new UnavailablePermissionBackend(), log);
        // Completes rather than throwing, so the hire path can chain it unconditionally.
        assertThat(offline.ensure(io.paradaux.jobs.api.model.JobId.of("professions", "lawyer")))
                .isCompleted();
    }

    @Test
    void unavailableBackendReadsAreEmptyAndWritesFail() {
        PermissionBackend offline = new UnavailablePermissionBackend();
        assertThat(offline.available()).isFalse();
        assertThat(offline.cachedInheritedGroups(java.util.UUID.randomUUID())).isEmpty();
        assertThat(offline.inheritedGroups(java.util.UUID.randomUUID()).join()).isEmpty();
        assertThat(offline.directHolders("x").join()).isEmpty();
        assertThat(offline.addGroup(java.util.UUID.randomUUID(), "x").join())
                .isEqualTo(io.paradaux.jobs.permissions.MutationOutcome.FAILED);
        assertThat(offline.removeGroup(java.util.UUID.randomUUID(), "x").join())
                .isEqualTo(io.paradaux.jobs.permissions.MutationOutcome.FAILED);
    }

    @Test
    void reportsIncludeNothingWhenNoJobsAreConfigured() {
        GroupProvisioner provisioner = new GroupProvisioner(registryFor("types: {}\n"), backend, log);
        assertThat(provisioner.provisionAll().considered()).isZero();
    }
}
