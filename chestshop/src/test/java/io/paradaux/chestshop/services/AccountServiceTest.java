package io.paradaux.chestshop.services;

import io.paradaux.chestshop.configuration.Properties;
import io.paradaux.chestshop.database.Account;
import io.paradaux.chestshop.mappers.AccountMapper;
import io.paradaux.chestshop.players.PlayerDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the Bukkit-free account logic now that it lives in a service over a
 * pluggable {@link AccountMapper} — the row-count adjustment and the unique
 * shortened-name allocation, both formerly buried in the static {@code NameManager}
 * God-class and untestable. The fake mapper stands in for the SQLite store.
 */
class AccountServiceTest {

    /** In-memory {@link AccountMapper}: a fixed row count and a set of taken short names. */
    private static final class FakeAccountMapper implements AccountMapper {
        long count = 0;
        final Set<String> takenShortNames = new HashSet<>();

        @Override public Account findLatestByUuid(UUID uuid) { return null; }
        @Override public Account findLatestByName(String name) { return null; }
        @Override public Account findByShortName(String shortName) {
            return takenShortNames.contains(shortName)
                    ? new Account("taken", shortName, UUID.randomUUID())
                    : null;
        }
        @Override public Account findByUuidAndName(UUID uuid, String name) { return null; }
        @Override public void save(Account account) { count++; }
        @Override public long count() { return count; }
    }

    private FakeAccountMapper mapper;
    private AccountService service;

    @BeforeEach
    void setUp() {
        Properties.CACHE_SIZE = 128;
        mapper = new FakeAccountMapper();
        service = new AccountService(mapper, () -> null);
    }

    @Test
    void getAccountCount_excludesTheAdminAccountRow() {
        mapper.count = 5; // four players plus the always-present admin account

        assertThat(service.getAccountCount()).isEqualTo(4);
    }

    @Test
    void getNewShortenedName_returnsTheStrippedName_whenItIsFree() {
        String name = service.getNewShortenedName(new PlayerDTO(UUID.randomUUID(), "Acrobot"));

        assertThat(name).isEqualTo("Acrobot");
    }

    @Test
    void getNewShortenedName_disambiguates_whenTheBaseNameIsTaken() {
        mapper.takenShortNames.add("Acrobot");

        String name = service.getNewShortenedName(new PlayerDTO(UUID.randomUUID(), "Acrobot"));

        assertThat(name).isNotEqualTo("Acrobot");
        assertThat(name).startsWith("Acrobot:");
        assertThat(mapper.findByShortName(name)).isNull(); // the allocated name is actually free
    }
}
