package io.paradaux.treasury.commands;

import io.paradaux.hibernia.framework.i18n.Message;
import io.paradaux.treasury.commands.resolvers.PayTarget;
import io.paradaux.treasury.services.AccountService;
import io.paradaux.treasury.services.LedgerService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayCommandTest {

    /** A name that exists as both a player alt and a GOVERNMENT account on DC. */
    private static final String COLLIDING_NAME = "DCGovernmentOR";

    @Mock AccountService accountService;
    @Mock LedgerService ledgerService;
    @Mock Message message;
    @Mock Player sender;
    @Mock OfflinePlayer targetPlayer;

    private PayCommand command;
    private MockedStatic<Bukkit> bukkit;

    @BeforeEach
    void setUp() {
        command = new PayCommand(accountService, ledgerService, message);
        bukkit = mockStatic(Bukkit.class);
    }

    @AfterEach
    void tearDown() {
        bukkit.close();
    }

    @Test
    void pay_refusesAndPromptsWhenNameIsBothPlayerAndGovernment() {
        // The name resolves to a cached player AND a non-archived gov account.
        bukkit.when(() -> Bukkit.getOfflinePlayerIfCached(COLLIDING_NAME)).thenReturn(targetPlayer);
        when(targetPlayer.hasPlayedBefore()).thenReturn(true);
        when(accountService.governmentAccountExists(COLLIDING_NAME)).thenReturn(true);

        command.pay(sender, new PayTarget(COLLIDING_NAME), new BigDecimal("100"));

        // It must refuse (deterministically) and tell the sender to disambiguate —
        // never silently move money to either account.
        verify(message).send(sender, "treasury.pay.ambiguous", "target", COLLIDING_NAME);
        verify(ledgerService, never()).transfer(any());
    }

    @Test
    void pay_paysGovernmentWhenNameIsGovOnly_uncachedPlayer() {
        // No cached player for the name → falls through to the gov account.
        bukkit.when(() -> Bukkit.getOfflinePlayerIfCached(COLLIDING_NAME)).thenReturn(null);

        var gov = new io.paradaux.treasury.model.economy.Account();
        gov.setAccountId(49929);
        gov.setDisplayName(COLLIDING_NAME);
        gov.setArchived(false);
        when(accountService.getGovernmentAccountByName(COLLIDING_NAME)).thenReturn(gov);
        when(accountService.getOrCreatePersonalAccountId(any())).thenReturn(7);
        when(accountService.getBalanceReadOnly(7)).thenReturn(new BigDecimal("1000"));
        when(sender.getUniqueId()).thenReturn(java.util.UUID.randomUUID());

        command.pay(sender, new PayTarget(COLLIDING_NAME), new BigDecimal("100"));

        // governmentAccountExists isn't consulted (no cached player), and the
        // payment goes to the gov account — no ambiguity prompt.
        verify(message, never()).send(sender, "treasury.pay.ambiguous", "target", COLLIDING_NAME);
        verify(ledgerService).transfer(any());
    }
}
