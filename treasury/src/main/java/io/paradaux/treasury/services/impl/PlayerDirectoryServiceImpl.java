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
