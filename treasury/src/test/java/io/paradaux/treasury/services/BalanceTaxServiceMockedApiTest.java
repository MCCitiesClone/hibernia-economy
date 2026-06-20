package io.paradaux.treasury.services;

import io.paradaux.treasury.api.TaxApi;
import io.paradaux.treasury.mappers.AccountMapper;
import io.paradaux.treasury.mappers.PlayerLoginMapper;
import io.paradaux.treasury.model.config.BalanceTaxConfiguration;
import io.paradaux.treasury.model.economy.Account;
import io.paradaux.treasury.model.economy.AccountBalance;
import io.paradaux.treasury.model.tax.TaxCollection;
import io.paradaux.treasury.model.tax.TaxResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BalanceTaxService} branches that are awkward to trigger
 * end-to-end: {@code Skipped}/{@code Failed} result handling, and the missing
 * balance row short-circuit.
 */
@ExtendWith(MockitoExtension.class)
class BalanceTaxServiceMockedApiTest {

    @Mock TaxApi taxApi;
    @Mock AccountMapper accountMapper;
    @Mock PlayerLoginMapper loginMapper;

    private BalanceTaxService svc;

    private static BalanceTaxConfiguration enabledConfig() {
        NavigableMap<BigDecimal, BigDecimal> brackets = new TreeMap<>();
        brackets.put(new BigDecimal("0.00"),       BigDecimal.ZERO);
        brackets.put(new BigDecimal("100000.00"),  new BigDecimal("0.01"));
        return BalanceTaxConfiguration.forTesting(true, "DCGovernment", brackets);
    }

    @BeforeEach
    void setUp() {
        svc = new BalanceTaxService(enabledConfig(), taxApi, accountMapper, loginMapper);
    }

    @Test
    void missingBalanceRow_shortCircuitsBeforeCollect() {
        UUID player = UUID.randomUUID();
        long now = 1_700_000_000L;
        when(loginMapper.findLastLogin(player)).thenReturn(now - 86_400);
        when(accountMapper.findPersonalAccountId(player)).thenReturn(42);
        when(accountMapper.readBalance(42)).thenReturn(null);

        svc.processLogin(player, now);

        verify(loginMapper).upsertLogin(player, now);
        verify(taxApi, never()).collectTax(any());
    }

    @Test
    void nullBalanceField_shortCircuits() {
        UUID player = UUID.randomUUID();
        long now = 1_700_000_000L;
        when(loginMapper.findLastLogin(player)).thenReturn(now - 86_400);
        when(accountMapper.findPersonalAccountId(player)).thenReturn(42);
        AccountBalance ab = new AccountBalance();
        ab.setBalance(null);
        when(accountMapper.readBalance(42)).thenReturn(ab);

        svc.processLogin(player, now);

        verify(taxApi, never()).collectTax(any());
    }

    @Test
    void apiReturnsSkipped_logsAndContinues() {
        UUID player = UUID.randomUUID();
        long now = 1_700_000_000L;
        long lastLogin = now - 7L * 24 * 3600 / 2;
        when(loginMapper.findLastLogin(player)).thenReturn(lastLogin);
        when(accountMapper.findPersonalAccountId(player)).thenReturn(42);
        AccountBalance ab = new AccountBalance();
        ab.setBalance(new BigDecimal("100000.00"));
        when(accountMapper.readBalance(42)).thenReturn(ab);

        Account dest = new Account();
        dest.setAccountId(99);
        when(accountMapper.findGovernmentAccountByName("DCGovernment")).thenReturn(dest);

        when(taxApi.collectTax(any(TaxCollection.class)))
                .thenReturn(new TaxResult.Skipped("dedup"));

        svc.processLogin(player, now);

        verify(taxApi).collectTax(any(TaxCollection.class));
    }

    @Test
    void apiReturnsFailed_logsAndContinues() {
        UUID player = UUID.randomUUID();
        long now = 1_700_000_000L;
        long lastLogin = now - 7L * 24 * 3600 / 2;
        when(loginMapper.findLastLogin(player)).thenReturn(lastLogin);
        when(accountMapper.findPersonalAccountId(player)).thenReturn(42);
        AccountBalance ab = new AccountBalance();
        ab.setBalance(new BigDecimal("100000.00"));
        when(accountMapper.readBalance(42)).thenReturn(ab);

        Account dest = new Account();
        dest.setAccountId(99);
        when(accountMapper.findGovernmentAccountByName("DCGovernment")).thenReturn(dest);

        when(taxApi.collectTax(any(TaxCollection.class)))
                .thenReturn(new TaxResult.Failed("boom"));

        svc.processLogin(player, now);

        verify(taxApi).collectTax(any(TaxCollection.class));
    }
}
