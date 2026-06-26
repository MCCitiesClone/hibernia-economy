package io.paradaux.chestshop.services;

import io.paradaux.chestshop.configuration.Properties;
import io.paradaux.chestshop.dao.AccountRepository;
import io.paradaux.chestshop.database.Account;
import io.paradaux.chestshop.players.PlayerDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the Bukkit-free account logic now that it lives in a service over a
 * pluggable {@link AccountRepository} — the row-count adjustment and the unique
 * shortened-name allocation, both formerly buried in the static {@code NameManager}
 * God-class and untestable. The fake repository stands in for the SQLite store.
 */
class AccountServiceTest {

    /** In-memory {@link AccountRepository}: a fixed row count and a set of taken short names. */
    private static final class FakeAccountRepository implements AccountRepository {
        long count = 0;
        final Set<String> takenShortNames = new HashSet<>();

        @Override public Optional<Account> findLatestByUuid(UUID uuid) { return Optional.empty(); }
        @Override public Optional<Account> findLatestByName(String name) { return Optional.empty(); }
        @Override public Optional<Account> findByShortName(String shortName) {
            return takenShortNames.contains(shortName)
                    ? Optional.of(new Account("taken", shortName, UUID.randomUUID()))
                    : Optional.empty();
        }
        @Override public Optional<Account> findByUuidAndName(UUID uuid, String name) { return Optional.empty(); }
        @Override public void save(Account account) { count++; }
        @Override public long count() { return count; }
    }

    private FakeAccountRepository repository;
    private AccountService service;

    @BeforeEach
    void setUp() {
        Properties.CACHE_SIZE = 128;
        repository = new FakeAccountRepository();
        service = new AccountService(repository);
    }

    @Test
    void getAccountCount_excludesTheAdminAccountRow() {
        repository.count = 5; // four players plus the always-present admin account

        assertThat(service.getAccountCount()).isEqualTo(4);
    }

    @Test
    void getNewShortenedName_returnsTheStrippedName_whenItIsFree() {
        String name = service.getNewShortenedName(new PlayerDTO(UUID.randomUUID(), "Acrobot"));

        assertThat(name).isEqualTo("Acrobot");
    }

    @Test
    void getNewShortenedName_disambiguates_whenTheBaseNameIsTaken() {
        repository.takenShortNames.add("Acrobot");

        String name = service.getNewShortenedName(new PlayerDTO(UUID.randomUUID(), "Acrobot"));

        assertThat(name).isNotEqualTo("Acrobot");
        assertThat(name).startsWith("Acrobot:");
        assertThat(repository.findByShortName(name)).isEmpty(); // the allocated name is actually free
    }
}
