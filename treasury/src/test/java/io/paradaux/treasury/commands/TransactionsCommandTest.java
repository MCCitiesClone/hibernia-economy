package io.paradaux.treasury.commands;

import io.paradaux.hibernia.framework.i18n.Message;
import io.paradaux.treasury.model.Page;
import io.paradaux.treasury.model.economy.Account;
import io.paradaux.treasury.model.economy.TransactionEntry;
import io.paradaux.treasury.services.AccountService;
import io.paradaux.treasury.services.DataExportService;
import io.paradaux.treasury.services.LedgerService;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Per-account authorization on the audit routes (ADT-18). The
 * {@code treasury.transactions.audit} node alone must not expose any account's
 * ledger: a genuine admin ({@code treasury.admin.inspect}) may audit anything,
 * everyone else is limited to accounts they can access.
 */
@ExtendWith(MockitoExtension.class)
class TransactionsCommandTest {

    private static final String ADMIN_NODE = "treasury.admin.inspect";
    private static final int ACCOUNT_ID = 5;

    @Mock AccountService accountService;
    @Mock LedgerService ledgerService;
    @Mock DataExportService dataExportService;
    @Mock Message message;
    @Mock Player viewer;

    private TransactionsCommand cmd;

    @BeforeEach
    void setUp() {
        cmd = new TransactionsCommand(accountService, ledgerService, dataExportService, message);
    }

    private static Page<TransactionEntry> emptyPage() {
        return new Page<>(List.of(), 0, 0, 10);
    }

    // ---------- auditaccount ----------

    @Test
    void auditAccount_nonAdminWithoutAccess_deniedAndNoLedgerRead() {
        UUID uuid = UUID.randomUUID();
        when(accountService.hasAccountByAccountId(ACCOUNT_ID)).thenReturn(true);
        when(viewer.hasPermission(ADMIN_NODE)).thenReturn(false);
        when(viewer.getUniqueId()).thenReturn(uuid);
        when(accountService.canAccessAccount(uuid, ACCOUNT_ID)).thenReturn(false);

        cmd.auditAccount(viewer, ACCOUNT_ID);

        verify(message).send(viewer, "treasury.transactions.audit.no-access");
        verify(ledgerService, never()).getTransactionHistory(anyInt(), anyInt(), anyInt());
        verify(accountService, never()).getAccountById(anyInt());
    }

    @Test
    void auditAccount_adminOverride_allowedWithoutMembershipCheck() {
        Account account = new Account();
        account.setAccountId(ACCOUNT_ID);
        account.setDisplayName("Acme Corp");
        when(accountService.hasAccountByAccountId(ACCOUNT_ID)).thenReturn(true);
        when(viewer.hasPermission(ADMIN_NODE)).thenReturn(true);
        when(accountService.getAccountById(ACCOUNT_ID)).thenReturn(account);
        when(ledgerService.getTransactionHistory(ACCOUNT_ID, 0, 10)).thenReturn(emptyPage());
        when(viewer.getName()).thenReturn("Auditor");

        cmd.auditAccount(viewer, ACCOUNT_ID);

        // Admin path never consults membership, and the ledger read happens.
        verify(accountService, never()).canAccessAccount(any(), anyInt());
        verify(ledgerService).getTransactionHistory(ACCOUNT_ID, 0, 10);
        verify(message).send(viewer, "treasury.transactions.audit.empty", "target", "Acme Corp");
        verify(message, never()).send(viewer, "treasury.transactions.audit.no-access");
    }

    @Test
    void auditAccount_memberWithAccess_allowed() {
        UUID uuid = UUID.randomUUID();
        Account account = new Account();
        account.setAccountId(ACCOUNT_ID);
        account.setDisplayName("Acme Corp");
        when(accountService.hasAccountByAccountId(ACCOUNT_ID)).thenReturn(true);
        when(viewer.hasPermission(ADMIN_NODE)).thenReturn(false);
        when(viewer.getUniqueId()).thenReturn(uuid);
        when(accountService.canAccessAccount(uuid, ACCOUNT_ID)).thenReturn(true);
        when(accountService.getAccountById(ACCOUNT_ID)).thenReturn(account);
        when(ledgerService.getTransactionHistory(ACCOUNT_ID, 0, 10)).thenReturn(emptyPage());
        when(viewer.getName()).thenReturn("Member");

        cmd.auditAccount(viewer, ACCOUNT_ID);

        verify(ledgerService).getTransactionHistory(ACCOUNT_ID, 0, 10);
        verify(message, never()).send(viewer, "treasury.transactions.audit.no-access");
    }

    @Test
    void auditAccount_unknownAccount_notFound_andNoAccessCheck() {
        when(accountService.hasAccountByAccountId(ACCOUNT_ID)).thenReturn(false);

        cmd.auditAccount(viewer, ACCOUNT_ID);

        verify(message).send(viewer, "treasury.transactions.audit.not-found");
        verify(accountService, never()).canAccessAccount(any(), anyInt());
        verify(ledgerService, never()).getTransactionHistory(anyInt(), anyInt(), anyInt());
    }

    // ---------- audit <player> ----------

    @Test
    void auditPlayer_nonAdminWithoutAccess_denied() {
        UUID viewerUuid = UUID.randomUUID();
        UUID targetUuid = UUID.randomUUID();
        OfflinePlayer target = org.mockito.Mockito.mock(OfflinePlayer.class);
        when(target.hasPlayedBefore()).thenReturn(true);
        when(target.getUniqueId()).thenReturn(targetUuid);
        when(accountService.findPersonalAccountId(targetUuid)).thenReturn(7);
        when(viewer.hasPermission(ADMIN_NODE)).thenReturn(false);
        when(viewer.getUniqueId()).thenReturn(viewerUuid);
        when(accountService.canAccessAccount(viewerUuid, 7)).thenReturn(false);

        cmd.auditPlayer(viewer, target);

        verify(message).send(viewer, "treasury.transactions.audit.no-access");
        verify(ledgerService, never()).getTransactionHistory(anyInt(), anyInt(), anyInt());
    }

    @Test
    void auditPlayer_adminOverride_allowed() {
        UUID targetUuid = UUID.randomUUID();
        OfflinePlayer target = org.mockito.Mockito.mock(OfflinePlayer.class);
        when(target.hasPlayedBefore()).thenReturn(true);
        when(target.getUniqueId()).thenReturn(targetUuid);
        when(target.getName()).thenReturn("Bob");
        when(accountService.findPersonalAccountId(targetUuid)).thenReturn(7);
        when(viewer.hasPermission(ADMIN_NODE)).thenReturn(true);
        when(ledgerService.getTransactionHistory(7, 0, 10)).thenReturn(emptyPage());
        when(viewer.getName()).thenReturn("Auditor");

        cmd.auditPlayer(viewer, target);

        verify(accountService, never()).canAccessAccount(any(), anyInt());
        verify(ledgerService).getTransactionHistory(7, 0, 10);
        verify(message).send(viewer, "treasury.transactions.audit.empty", "target", "Bob");
    }
}
