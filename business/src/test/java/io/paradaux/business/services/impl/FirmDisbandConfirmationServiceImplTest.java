package io.paradaux.business.services.impl;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class FirmDisbandConfirmationServiceImplTest {

    private final AtomicLong now = new AtomicLong(0L);
    private final UUID player = UUID.randomUUID();

    private FirmDisbandConfirmationServiceImpl svc() {
        return new FirmDisbandConfirmationServiceImpl(now::get);
    }

    @Test
    void requestThenConsume_withinTtl_succeeds() {
        FirmDisbandConfirmationServiceImpl svc = svc();
        svc.request(player, 1);
        now.set(FirmDisbandConfirmationServiceImpl.TTL_MS); // exactly at the boundary is still valid
        assertThat(svc.consume(player, 1)).isTrue();
    }

    @Test
    void consume_withoutRequest_fails() {
        assertThat(svc().consume(player, 1)).isFalse();
    }

    @Test
    void consume_wrongFirm_fails_andClearsPending() {
        FirmDisbandConfirmationServiceImpl svc = svc();
        svc.request(player, 1);
        // Confirming a different firm must not disband, and the stale pending is dropped.
        assertThat(svc.consume(player, 2)).isFalse();
        assertThat(svc.consume(player, 1)).isFalse();
    }

    @Test
    void consume_afterExpiry_fails() {
        FirmDisbandConfirmationServiceImpl svc = svc();
        svc.request(player, 1);
        now.set(FirmDisbandConfirmationServiceImpl.TTL_MS + 1);
        assertThat(svc.consume(player, 1)).isFalse();
    }

    @Test
    void consume_isSingleUse() {
        FirmDisbandConfirmationServiceImpl svc = svc();
        svc.request(player, 1);
        assertThat(svc.consume(player, 1)).isTrue();
        assertThat(svc.consume(player, 1)).isFalse();
    }
}
