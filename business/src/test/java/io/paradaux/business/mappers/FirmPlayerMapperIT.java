package io.paradaux.business.mappers;

import io.paradaux.business.model.FirmPlayer;
import io.paradaux.business.testsupport.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FirmPlayerMapperIT extends IntegrationTestBase {

    private FirmPlayerMapper mapper;

    @BeforeEach
    void open() {
        mapper = mapper(FirmPlayerMapper.class);
    }

    @Test
    void upsert_insertsThenUpdates() {
        UUID id = UUID.randomUUID();
        // Initial insert -> 1 affected row.
        assertThat(mapper.upsert(id.toString(), "Alice")).isEqualTo(1);
        // ON DUPLICATE KEY UPDATE in MariaDB returns 2 when the row exists and changes,
        // 1 when it stays the same. We just check `> 0` to stay portable.
        assertThat(mapper.upsert(id.toString(), "Alice2")).isPositive();

        FirmPlayer p = mapper.getByUuid(id.toString());
        assertThat(p.getCurrentName()).isEqualTo("Alice2");
        assertThat(p.getNameLower()).isEqualTo("alice2");
    }

    @Test
    void existsByUuid_returnsCount() {
        UUID id = UUID.randomUUID();
        assertThat(mapper.existsByUuid(id.toString())).isZero();
        mapper.upsert(id.toString(), "Alice");
        assertThat(mapper.existsByUuid(id.toString())).isEqualTo(1);
    }

    @Test
    void getByName_isCaseInsensitive() {
        UUID id = UUID.randomUUID();
        mapper.upsert(id.toString(), "Alice");
        assertThat(mapper.getByName("alice")).isNotNull();
        assertThat(mapper.getByName("ALICE")).isNotNull();
        assertThat(mapper.getByName("Alice")).isNotNull();
        assertThat(mapper.getByName("Bob")).isNull();
    }

    @Test
    void searchByPrefix_returnsMatches() {
        mapper.upsert(UUID.randomUUID().toString(), "Alice");
        mapper.upsert(UUID.randomUUID().toString(), "Alex");
        mapper.upsert(UUID.randomUUID().toString(), "Bob");

        List<FirmPlayer> hits = mapper.searchByPrefix("al", 10);
        assertThat(hits).extracting(FirmPlayer::getCurrentName)
                .containsExactlyInAnyOrder("Alice", "Alex");
    }

    @Test
    void updateName_returnsRowCount() {
        UUID id = UUID.randomUUID();
        mapper.upsert(id.toString(), "Alice");
        assertThat(mapper.updateName(id.toString(), "Alicia")).isEqualTo(1);
        assertThat(mapper.getByUuid(id.toString()).getCurrentName()).isEqualTo("Alicia");
    }
}
