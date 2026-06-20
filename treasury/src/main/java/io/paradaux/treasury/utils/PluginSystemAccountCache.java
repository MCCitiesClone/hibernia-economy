package io.paradaux.treasury.utils;

import com.google.inject.Inject;
import io.paradaux.treasury.services.AccountService;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class PluginSystemAccountCache {
    private final AccountService accountService;
    private final ConcurrentMap<String, Integer> cache = new ConcurrentHashMap<>();

    @Inject
    public PluginSystemAccountCache(AccountService accountService) {
        this.accountService = accountService;
    }

    public int getOrCreate(String pluginName, UUID treasuryOwner) {
        // computeIfAbsent is atomic per key; the delegate is @Transactional so
        // insertAccount + seedBalance commit together or roll back together.
        return cache.computeIfAbsent(pluginName,
                name -> accountService.getOrCreateSystemAccountId(name, treasuryOwner));
    }
}
