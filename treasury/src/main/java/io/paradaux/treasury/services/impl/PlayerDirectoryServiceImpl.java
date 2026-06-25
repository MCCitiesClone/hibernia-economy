package io.paradaux.treasury.services.impl;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.paradaux.treasury.mappers.EconomyPlayerMapper;
import io.paradaux.treasury.services.PlayerDirectoryService;
import org.mybatis.guice.transactional.Transactional;

import java.util.Optional;
import java.util.UUID;

@Singleton
public class PlayerDirectoryServiceImpl implements PlayerDirectoryService {

    private final EconomyPlayerMapper economyPlayers;

    @Inject
    public PlayerDirectoryServiceImpl(EconomyPlayerMapper economyPlayers) {
        this.economyPlayers = economyPlayers;
    }

    @Override
    @Transactional
    public void recordLogin(UUID playerUuid, String currentName, long epochSeconds) {
        // A name belongs to exactly one player at a time; evict any stale claim by a
        // different player first so the upsert can't collide on UNIQUE(name_lower)
        // when a name is changed or reused (the old holder re-registers on next login).
        economyPlayers.releaseNameFromOthers(currentName, playerUuid);
        economyPlayers.upsertLogin(playerUuid, currentName, epochSeconds);
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
