package io.paradaux.business.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FirmPlayerTest {

    @Test
    void defaultConstructedReturnsNullUuidInsteadOfThrowing() {
        // Previously NPE'd on the null playerUuid (ADT-35).
        assertThat(new FirmPlayer().getUniqueId()).isNull();
    }

    @Test
    void blankUuidReturnsNull() {
        FirmPlayer p = new FirmPlayer();
        p.setPlayerUuid("   ");
        assertThat(p.getUniqueId()).isNull();
    }

    @Test
    void validUuidIsParsed() {
        UUID id = UUID.randomUUID();
        FirmPlayer p = new FirmPlayer();
        p.setPlayerUuid(id.toString());
        assertThat(p.getUniqueId()).isEqualTo(id);
    }

    @Test
    void malformedUuidStillThrows() {
        FirmPlayer p = new FirmPlayer();
        p.setPlayerUuid("not-a-uuid");
        assertThatThrownBy(p::getUniqueId).isInstanceOf(IllegalArgumentException.class);
    }
}
