package io.paradaux.treasury.commands;

import io.paradaux.hibernia.framework.i18n.Message;
import io.paradaux.treasury.services.AccountService;
import io.paradaux.treasury.services.PlayerDirectoryService;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BalanceCommandTest {

    @Mock AccountService accountService;
    @Mock PlayerDirectoryService playerDirectory;
    @Mock Message message;
    @Mock CommandSender sender;
    @Mock OfflinePlayer target;

    @Test
    void balanceOther_uncachedPlayer_resolvesViaDirectory() {
        UUID uuid = UUID.randomUUID();
        BalanceCommand cmd = new BalanceCommand(accountService, playerDirectory, message);

        // Usercache miss: a synthetic OfflinePlayer that hasn't "played before"
        // but still carries the typed name.
        when(target.hasPlayedBefore()).thenReturn(false);
        when(target.isOnline()).thenReturn(false);
        when(target.getName()).thenReturn("Dodrio3");
        when(playerDirectory.resolveUuidByName("Dodrio3")).thenReturn(Optional.of(uuid));
        when(playerDirectory.resolveNameByUuid(uuid)).thenReturn(Optional.of("Dodrio3"));
        when(accountService.getBalanceByOwnerUuid(uuid)).thenReturn(new BigDecimal("42.00"));
        when(accountService.formatAmount(new BigDecimal("42.00"))).thenReturn("$42.00");

        cmd.balanceOther(sender, target);

        // Resolved by name despite the cache miss; balance reported for the real UUID.
        verify(accountService).getBalanceByOwnerUuid(uuid);
        verify(message).send(sender, "treasury.balance.other", "player", "Dodrio3", "balance", "$42.00");
        verify(message, never()).send(sender, "treasury.general.unknown-player");
    }

    @Test
    void balanceOther_uncachedAndNotInDirectory_unknownPlayer() {
        BalanceCommand cmd = new BalanceCommand(accountService, playerDirectory, message);

        when(target.hasPlayedBefore()).thenReturn(false);
        when(target.isOnline()).thenReturn(false);
        when(target.getName()).thenReturn("ghost");
        when(playerDirectory.resolveUuidByName("ghost")).thenReturn(Optional.empty());

        cmd.balanceOther(sender, target);

        verify(message).send(sender, "treasury.general.unknown-player");
        verify(accountService, never()).getBalanceByOwnerUuid(any());
    }
}
