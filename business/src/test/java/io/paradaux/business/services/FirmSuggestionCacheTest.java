package io.paradaux.business.services;

import io.paradaux.business.model.Firm;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FirmSuggestionCacheTest {

    @Mock FirmService firms;

    private final AtomicLong now = new AtomicLong(0L);
    private final LongSupplier clock = now::get;

    private FirmSuggestionCache cache() {
        return new FirmSuggestionCache(firms, clock);
    }

    private static Firm firm(String displayName) {
        Firm f = new Firm();
        f.setDisplayName(displayName);
        return f;
    }

    private static Firm archivedFirm(String displayName) {
        Firm f = firm(displayName);
        f.setArchived(true);
        return f;
    }

    // ---- playerFirmNames: caching + TTL ----------------------------------------

    @Test
    void playerFirmNames_returnsDisplayNames() {
        UUID player = UUID.randomUUID();
        when(firms.listOwnedOrMemberFirms(player)).thenReturn(List.of(firm("Acme"), firm("Globex")));

        assertThat(cache().playerFirmNames(player)).containsExactlyInAnyOrder("Acme", "Globex");
    }

    @Test
    void playerFirmNames_cachesWithinTtl_thenRefreshes() {
        UUID player = UUID.randomUUID();
        when(firms.listOwnedOrMemberFirms(player))
                .thenReturn(List.of(firm("Acme")))
                .thenReturn(List.of(firm("Acme"), firm("Globex")));

        FirmSuggestionCache c = cache();

        // First call populates the cache.
        assertThat(c.playerFirmNames(player)).containsExactly("Acme");
        // Within TTL → served from cache, no second DB hit.
        now.set(FirmSuggestionCache.TTL_MS);
        assertThat(c.playerFirmNames(player)).containsExactly("Acme");
        verify(firms, times(1)).listOwnedOrMemberFirms(player);

        // Past TTL → refreshed from DB.
        now.set(FirmSuggestionCache.TTL_MS + 1);
        assertThat(c.playerFirmNames(player)).containsExactlyInAnyOrder("Acme", "Globex");
        verify(firms, times(2)).listOwnedOrMemberFirms(player);
    }

    @Test
    void playerFirmNames_dropsNullDisplayNames() {
        UUID player = UUID.randomUUID();
        when(firms.listOwnedOrMemberFirms(player)).thenReturn(List.of(firm("Acme"), firm(null)));

        assertThat(cache().playerFirmNames(player)).containsExactly("Acme");
    }

    @Test
    void playerFirmNames_dropsDisbandedFirms() {
        UUID player = UUID.randomUUID();
        // A firm the player used to belong to but is now disbanded must not be suggested,
        // since it no longer resolves on command paths.
        when(firms.listOwnedOrMemberFirms(player)).thenReturn(List.of(firm("Acme"), archivedFirm("OldCorp")));

        assertThat(cache().playerFirmNames(player)).containsExactly("Acme");
    }

    // ---- activeFirmNames: caching + TTL ----------------------------------------

    @Test
    void activeFirmNames_cachesWithinTtl_thenRefreshes() {
        when(firms.listAllActiveFirms())
                .thenReturn(List.of(firm("Acme")))
                .thenReturn(List.of(firm("Acme"), firm("Initech")));

        FirmSuggestionCache c = cache();

        assertThat(c.activeFirmNames()).containsExactly("Acme");
        now.set(FirmSuggestionCache.TTL_MS);
        assertThat(c.activeFirmNames()).containsExactly("Acme");
        verify(firms, times(1)).listAllActiveFirms();

        now.set(FirmSuggestionCache.TTL_MS + 1);
        assertThat(c.activeFirmNames()).containsExactlyInAnyOrder("Acme", "Initech");
        verify(firms, times(2)).listAllActiveFirms();
    }

    // ---- match(): filtering, ordering, dedupe, cap -----------------------------

    @Test
    void match_filtersCaseInsensitivePrefix() {
        List<String> out = FirmSuggestionCache.match(List.of("Acme", "Apex", "Globex"), "a", 20);
        assertThat(out).containsExactly("Acme", "Apex");
    }

    @Test
    void match_nullPrefixReturnsAllSortedCaseInsensitive() {
        List<String> out = FirmSuggestionCache.match(List.of("globex", "Acme", "apex"), null, 20);
        assertThat(out).containsExactly("Acme", "apex", "globex");
    }

    @Test
    void match_distinctAndCapped() {
        List<String> out = FirmSuggestionCache.match(List.of("Acme", "Acme", "Apex", "Anvil"), "a", 2);
        assertThat(out).containsExactly("Acme", "Anvil");
    }

    @Test
    void match_emptyPoolIsEmpty() {
        assertThat(FirmSuggestionCache.match(Set.of(), "x", 20)).isEmpty();
    }
}
