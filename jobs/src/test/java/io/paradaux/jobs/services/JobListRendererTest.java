package io.paradaux.jobs.services;

import io.paradaux.hibernia.framework.i18n.Message;
import io.paradaux.jobs.api.model.HeldJob;
import io.paradaux.jobs.api.model.JobId;
import io.paradaux.jobs.model.config.JobsYaml;
import org.bukkit.command.CommandSender;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Section grouping, filtering and the inherited marker. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JobListRendererTest {

    private static final String CONFIG = """
            listing-commands:
              licenses: licenses
            types:
              government:
                display-name: "Government"
                order: 10
                jobs:
                  president: { group: president }
              licenses:
                display-name: "Licences"
                order: 50
                jobs:
                  firearms: { group: license-firearms }
            """;

    @Mock private Message message;
    @Mock private JobService jobs;
    @Mock private CommandSender sender;

    private final Logger log = Logger.getLogger("JobListRendererTest");
    private final UUID subject = UUID.randomUUID();

    private JobSnapshot snapshot;
    private JobListRenderer renderer;

    private void build(String yaml) {
        snapshot = JobSnapshot.build(
                JobsYaml.parse(YamlConfiguration.loadConfiguration(new StringReader(yaml))), log);
        JobRegistry registry = new JobRegistry() {
            @Override public JobSnapshot snapshot() { return snapshot; }
            @Override public void rebuild() { }
        };
        renderer = new JobListRenderer(message, jobs, registry);
    }

    @BeforeEach
    void setUp() {
        build(CONFIG);
    }

    private HeldJob held(String type, String job, String name, String typeName, boolean direct) {
        return new HeldJob(JobId.of(type, job), name, type, typeName, direct);
    }

    private List<String> sentKeys() {
        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(message, atLeastOnce()).send(eq(sender), keys.capture(), any(Object[].class));
        return keys.getAllValues();
    }

    @Test
    void jobsAreGroupedUnderTheirTypeSections() {
        when(jobs.heldJobs(subject)).thenReturn(CompletableFuture.completedFuture(List.of(
                held("government", "president", "President", "Government", true),
                held("licenses", "firearms", "Firearms Licence", "Licences", true))));

        renderer.render(sender, subject, "evan", JobListRenderer.Filter.all());

        List<String> keys = sentKeys();
        assertThat(keys).startsWith("jobs.list.header");
        assertThat(keys).containsSubsequence("jobs.list.section", "jobs.list.section");
        // Two section headers, one per type that has holdings.
        assertThat(keys.stream().filter("jobs.list.section"::equals).count()).isEqualTo(2);
    }

    @Test
    void anInheritedJobUsesTheMarkedEntryKey() {
        when(jobs.heldJobs(subject)).thenReturn(CompletableFuture.completedFuture(List.of(
                held("government", "president", "President", "Government", false))));

        renderer.render(sender, subject, "evan", JobListRenderer.Filter.all());

        // The inherited variant is plain text: there is nothing to click through to,
        // because /quit and /fire cannot remove it.
        assertThat(sentKeys()).contains("jobs.list.entry-inherited")
                .doesNotContain("jobs.list.entry");
    }

    @Test
    void aFilteredListingShowsOnlyThatType() {
        when(jobs.heldJobsOfType(subject, "licenses")).thenReturn(
                CompletableFuture.completedFuture(List.of(
                        held("licenses", "firearms", "Firearms Licence", "Licences", true))));

        renderer.render(sender, subject, "evan", JobListRenderer.Filter.only("licenses"));

        verify(jobs).heldJobsOfType(subject, "licenses");
        assertThat(sentKeys().stream().filter("jobs.list.section"::equals).count()).isEqualTo(1);
    }

    @Test
    void anEmptyListingUsesTheAppropriateEmptyMessage() {
        when(jobs.heldJobs(subject)).thenReturn(CompletableFuture.completedFuture(List.of()));
        renderer.render(sender, subject, "evan", JobListRenderer.Filter.all());
        assertThat(sentKeys()).containsExactly("jobs.list.empty");
    }

    @Test
    void anEmptyFilteredListingNamesTheType() {
        when(jobs.heldJobsOfType(subject, "licenses"))
                .thenReturn(CompletableFuture.completedFuture(List.of()));
        renderer.render(sender, subject, "evan", JobListRenderer.Filter.only("licenses"));
        assertThat(sentKeys()).containsExactly("jobs.list.type-empty");
    }

    @Test
    void anUnconfiguredTypeFilterSaysSoRatherThanShowingNothing() {
        renderer.render(sender, subject, "evan", JobListRenderer.Filter.only("nonexistent"));
        assertThat(sentKeys()).containsExactly("jobs.list.type-unconfigured");
    }

    @Test
    void showEmptyTypesRendersEverySectionIncludingEmptyOnes() {
        build(CONFIG.replace("listing-commands:", "show-empty-types: true\nlisting-commands:"));
        when(jobs.heldJobs(subject)).thenReturn(CompletableFuture.completedFuture(List.of(
                held("government", "president", "President", "Government", true))));

        renderer.render(sender, subject, "evan", JobListRenderer.Filter.all());

        List<String> keys = sentKeys();
        assertThat(keys.stream().filter("jobs.list.section"::equals).count()).isEqualTo(2);
        assertThat(keys).contains("jobs.list.section-empty");
    }

    @Test
    void theListingTypeMappingComesFromConfiguration() {
        assertThat(renderer.listingType("licenses")).contains("licenses");
        assertThat(renderer.listingType("qualifications")).isEmpty();
    }

    @Test
    void filterHelpersBehaveAsExpected() {
        assertThat(JobListRenderer.Filter.all().isAll()).isTrue();
        assertThat(JobListRenderer.Filter.only("licenses").isAll()).isFalse();
        assertThat(JobListRenderer.Filter.only("licenses").typeKey()).isEqualTo("licenses");
    }
}
