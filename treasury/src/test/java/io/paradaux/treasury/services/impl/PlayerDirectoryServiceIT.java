package io.paradaux.treasury.services.impl;

import io.paradaux.treasury.services.PlayerDirectoryService;
import io.paradaux.treasury.testsupport.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerDirectoryServiceIT extends IntegrationTestBase {

    private PlayerDirectoryService directory;

    @BeforeEach
    void setUp() {
        directory = injector.getInstance(PlayerDirectoryService.class);
    }

    @Test
    void recordLogin_thenResolveByNameAndUuid() {
        UUID uuid = UUID.randomUUID();
        directory.recordLogin(uuid, "Dodrio3", 1000L);

        assertThat(directory.resolveUuidByName("Dodrio3")).contains(uuid);
        assertThat(directory.resolveNameByUuid(uuid)).contains("Dodrio3");
    }

    @Test
    void resolveByName_isCaseInsensitiveAndTrimmed() {
        UUID uuid = UUID.randomUUID();
        directory.recordLogin(uuid, "DearEv", 1000L);

        assertThat(directory.resolveUuidByName("dearev")).contains(uuid);
        assertThat(directory.resolveUuidByName("  DEAREV ")).contains(uuid);
    }

    @Test
    void resolveByName_resolvesBedrockNameVerbatim() {
        UUID uuid = UUID.randomUUID();
        directory.recordLogin(uuid, ".Savannah212467", 1000L);

        // The leading Floodgate dot is preserved and the name resolves regardless
        // of usercache state — the whole point for PAR-150 / PAR-149.
        assertThat(directory.resolveUuidByName(".Savannah212467")).contains(uuid);
    }

    @Test
    void resolveByName_unknownOrBlank_isEmpty() {
        assertThat(directory.resolveUuidByName("nobody")).isEmpty();
        assertThat(directory.resolveUuidByName("")).isEmpty();
        assertThat(directory.resolveUuidByName(null)).isEmpty();
    }

    @Test
    void resolveNameByUuid_unknown_isEmpty() {
        assertThat(directory.resolveNameByUuid(UUID.randomUUID())).isEmpty();
    }

    @Test
    void recordLogin_upsertUpdatesNameOnReLogin() {
        UUID uuid = UUID.randomUUID();
        directory.recordLogin(uuid, "OldName", 1000L);
        directory.recordLogin(uuid, "NewName", 2000L);

        assertThat(directory.resolveNameByUuid(uuid)).contains("NewName");
        // The old name no longer resolves to this player (same row, name replaced).
        assertThat(directory.resolveUuidByName("OldName")).isEmpty();
    }
}
