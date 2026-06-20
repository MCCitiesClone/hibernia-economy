package io.paradaux.business.mappers;

import io.paradaux.business.model.Firm;
import io.paradaux.business.testsupport.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FirmAdminMapperIT extends IntegrationTestBase {

    private FirmAdminMapper admin;
    private FirmMapper firms;

    @BeforeEach
    void open() {
        admin = mapper(FirmAdminMapper.class);
        firms = mapper(FirmMapper.class);
    }

    @Test
    void deleteAllFirms_removesEveryFirmOwnedByPlayer() {
        UUID owner = UUID.randomUUID();
        Firm a = newFirm("A", owner); firms.createFirm(a);
        Firm b = newFirm("B", owner); firms.createFirm(b);

        UUID otherOwner = UUID.randomUUID();
        Firm c = newFirm("C", otherOwner); firms.createFirm(c);

        admin.deleteAllFirms(owner.toString());

        assertThat(firms.getFirmById(a.getFirmId())).isNull();
        assertThat(firms.getFirmById(b.getFirmId())).isNull();
        assertThat(firms.getFirmById(c.getFirmId())).isNotNull();
    }

    private Firm newFirm(String name, UUID owner) {
        Firm f = new Firm();
        f.setDisplayName(name);
        f.setProprietorUuid(owner.toString());
        return f;
    }
}
