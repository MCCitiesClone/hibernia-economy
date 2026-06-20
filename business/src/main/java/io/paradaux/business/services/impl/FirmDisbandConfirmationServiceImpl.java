package io.paradaux.business.services.impl;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.paradaux.business.services.FirmDisbandConfirmationService;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * In-memory implementation: one pending confirmation per player, expiring after
 * {@link #TTL_MS}. A confirmation only matches the firm it was issued for, so a
 * proprietor can't warn on one firm and confirm-disband another (PAR-93).
 */
@Singleton
public class FirmDisbandConfirmationServiceImpl implements FirmDisbandConfirmationService {

    static final long TTL_MS = 60_000L;

    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    @Inject
    public FirmDisbandConfirmationServiceImpl() {
        this(System::currentTimeMillis);
    }

    FirmDisbandConfirmationServiceImpl(LongSupplier clock) {
        this.clock = clock;
    }

    @Override
    public void request(UUID player, int firmId) {
        pending.put(player, new Pending(firmId, clock.getAsLong() + TTL_MS));
    }

    @Override
    public boolean consume(UUID player, int firmId) {
        Pending p = pending.remove(player);
        return p != null && p.firmId == firmId && clock.getAsLong() <= p.expiresAt;
    }

    private record Pending(int firmId, long expiresAt) {}
}
