package io.paradaux.chestshop.services.impl;

import io.paradaux.chestshop.model.Account;
import io.paradaux.chestshop.model.config.ChestShopConfiguration;
import io.paradaux.chestshop.services.AccountService;
import io.paradaux.chestshop.services.ItemService;
import io.paradaux.chestshop.services.AdminBypassService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Coverage for the Treasury-independent surface of {@link EconomyServiceImpl}.
 *
 * <p><b>Scope limitation.</b> Most of {@code EconomyServiceImpl} settles money by directly
 * constructing and calling the real Treasury API — {@code new TransferRequest(...)},
 * {@code treasury.transfer(...)}, {@code taxApi.collectRateTax(...)}, {@code TaxResult} pattern
 * matching, {@code AccountType}, {@code Idempotency.sha256(...)} — none of which exist on the
 * ChestShop <em>test runtime</em> classpath ({@code treasury-api} is a {@code compileOnly} project
 * dep). Those paths therefore can't be exercised here without the real API on the test runtime
 * (the one-line {@code testRuntimeOnly(project(":treasury:treasury-api"))} build change that is out
 * of scope for this task). This class covers only the methods that never reach the ledger:
 * {@code canHold}, {@code isOwnerEconomicallyActive}, and {@code format} before Treasury is bound.
 */
class EconomyServiceImplTest {

    private AccountService accounts;
    private ItemService items;
    private ChestShopConfiguration config;
    private AdminBypassService adminBypass;
    private EconomyServiceImpl service;

    @BeforeEach
    void setUp() {
        accounts = mock(AccountService.class);
        items = mock(ItemService.class);
        config = mock(ChestShopConfiguration.class);
        adminBypass = mock(AdminBypassService.class);
        service = new EconomyServiceImpl(accounts, items, config, adminBypass);
    }

    @Test
    void canHold_alwaysTrue() {
        assertThat(service.canHold(UUID.randomUUID(), new BigDecimal("100"))).isTrue();
    }

    @Test
    void isOwnerEconomicallyActive_trueForLimitedOwner() {
        assertThat(service.isOwnerEconomicallyActive(false)).isTrue();
    }

    @Test
    void isOwnerEconomicallyActive_unlimitedOwner_dependsOnServerAccount() {
        when(accounts.getServerEconomyAccount()).thenReturn(null);
        assertThat(service.isOwnerEconomicallyActive(true)).isFalse();

        when(accounts.getServerEconomyAccount()).thenReturn(new Account("Server", "Server", UUID.randomUUID()));
        assertThat(service.isOwnerEconomicallyActive(true)).isTrue();
    }

    @Test
    void format_beforeTreasuryBound_returnsPlainString() {
        // Treasury handle is null until bind() runs at enable time → plain-string fallback.
        assertThat(service.format(new BigDecimal("12.50"))).isEqualTo("12.50");
    }
}
