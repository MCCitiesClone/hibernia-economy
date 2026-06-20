package io.paradaux.treasury.model.economy;

import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferRequest(
        int fromAccountId,
        int toAccountId,
        BigDecimal amount,
        String message,
        UUID initiator,
        @Nullable UUID authorizer,
        @Nullable String pluginSystem,
        byte @Nullable [] dedupKey) {}
