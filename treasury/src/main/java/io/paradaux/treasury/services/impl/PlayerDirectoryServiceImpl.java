package io.paradaux.treasury.services.impl;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.paradaux.treasury.mappers.EconomyPlayerMapper;
import io.paradaux.treasury.services.PlayerDirectoryService;
import org.mybatis.guice.transactional.Transactional;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class PlayerDirectoryServiceImpl implements PlayerDirectoryService {

    private final EconomyPlayerMapper economyPlayers;

    /**
     * Per-player locks serialising concurrent {@link #recordLogin} calls for the
     * same UUID. Without this, two async tasks (a real login racing a manual
     * balance-tax trigger) could both read the same previous last-login epoch and
     * each prorate tax over an overlapping period — double-charging.
     */
    private final ConcurrentHashMap<UUID, Object> loginLocks = new ConcurrentHashMap<>();

    @Inject
    public PlayerDirectoryServiceImpl(EconomyPlayerMapper economyPlayers) {
        this.economyPlayers = economyPlayers;
    }

    @Override
    @Transactional
    public Long recordLogin(UUID playerUuid, String currentName, long epochSeconds) {
        Object lock = loginLocks.computeIfAbsent(playerUuid, k -> new Object());
        synchronized (lock) {
            // Capture the previous login before overwriting it; the caller prorates
            // against this so it must be read and replaced as one atomic step.
            Long previousEpoch = economyPlayers.findLastLogin(playerUuid);

            // A name belongs to exactly one player at a time; evict any stale claim by a
            // different player first so the upsert can't collide on UNIQUE(name_lower)
            // when a name is changed or reused (the old holder re-registers on next login).
            economyPlayers.releaseNameFromOthers(currentName, playerUuid);
            economyPlayers.upsertLogin(playerUuid, currentName, epochSeconds);

            return previousEpoch;
        }
    }

    @Override
    @Transactional
    public Optional<UUID> resolveUuidByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(economyPlayers.findUuidByName(name.trim()));
    }

    @Override
    @Transactional
    public Optional<String> resolveNameByUuid(UUID playerUuid) {
        return Optional.ofNullable(economyPlayers.findNameByUuid(playerUuid));
    }
}
