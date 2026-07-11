package io.paradaux.treasuryapi.commands;

import io.paradaux.hibernia.framework.i18n.Message;
import io.paradaux.treasuryapi.TreasuryAPI;
import io.paradaux.treasuryapi.mappers.ExplorerUiMapper;
import io.paradaux.treasuryapi.services.KeycloakAdminClient;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for link-code redemption in {@link UiAccessHandler#doLink}. The
 * single-use guarantee hinges on the conditional DELETE ({@code claimLinkCode})
 * being authoritative: only the caller whose claim reports one affected row may
 * write the identity. Both redemption routes run {@code @Async}, so a concurrent
 * loser (or an already-redeemed / expired code) must be rejected without
 * upserting an identity or touching Keycloak. These verify the handler's branch
 * on the claim's affected-row count rather than any timing behaviour.
 */
class UiAccessHandlerTest {

    private ExplorerUiMapper mapper;
    private KeycloakAdminClient keycloak;
    private Message message;
    private TreasuryAPI plugin;
    private UiAccessHandler handler;
    private Player sender;

    private final UUID playerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mapper = mock(ExplorerUiMapper.class);
        keycloak = mock(KeycloakAdminClient.class);
        message = mock(Message.class);
        plugin = mock(TreasuryAPI.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        handler = new UiAccessHandler(mapper, keycloak, message, plugin, null);

        sender = mock(Player.class);
        when(sender.getUniqueId()).thenReturn(playerId);
        when(sender.getName()).thenReturn("Alice");
    }

    @Test
    void doLink_winner_claimsRowThenUpsertsIdentity() {
        when(mapper.findValidLinkSub("ABC123")).thenReturn("sub-1");
        when(mapper.claimLinkCode("ABC123")).thenReturn(1);
        when(keycloak.isEnabled()).thenReturn(false);

        handler.doLink(sender, "abc123");

        verify(mapper).claimLinkCode("ABC123");
        verify(mapper).upsertIdentity(eq("sub-1"), eq(playerId), eq("Alice"), anyString());
        verify(message).send(sender, "treasuryapi.ui.link.success");
    }

    @Test
    void doLink_loser_claimReturnsZero_isRejectedWithoutUpsert() {
        // The code passed the SELECT (a concurrent winner had not yet deleted it),
        // but this caller's conditional DELETE claimed zero rows — it lost the race.
        when(mapper.findValidLinkSub("ABC123")).thenReturn("sub-1");
        when(mapper.claimLinkCode("ABC123")).thenReturn(0);

        handler.doLink(sender, "abc123");

        verify(mapper).claimLinkCode("ABC123");
        verify(mapper, never()).upsertIdentity(anyString(), any(), anyString(), anyString());
        verify(keycloak, never()).isEnabled();
        verify(message).send(sender, "treasuryapi.ui.link.invalid");
        verify(message, never()).send(sender, "treasuryapi.ui.link.success");
    }

    @Test
    void doLink_unknownCode_selectReturnsNull_neverAttemptsClaim() {
        when(mapper.findValidLinkSub("NOPE")).thenReturn(null);

        handler.doLink(sender, "nope");

        verify(mapper, never()).claimLinkCode(anyString());
        verify(mapper, never()).upsertIdentity(anyString(), any(), anyString(), anyString());
        verify(message).send(sender, "treasuryapi.ui.link.invalid");
    }

    // ---- Role-grant commands (ADT treasury-api-plugin/testing/0005) ----
    // doUserAdd/Remove/List resolve the target through Bukkit.getOfflinePlayer
    // (a static). We stub the static so the pure guard + mapper-routing logic is
    // exercised: invalid roles are rejected, an unknown player is refused (ADT-38),
    // and a valid grant lower-cases the role and writes with the granting UUID.

    @Test
    void doUserAdd_invalidRole_isRejected_neverWritesRole() {
        CommandSender console = mock(CommandSender.class);

        handler.doUserAdd(console, "wizard", "Bob");

        verify(mapper, never()).addRole(any(), anyString(), any());
        verify(message).send(console, "treasuryapi.ui.user.invalid-role", "role", "wizard");
    }

    @Test
    void doUserAdd_unknownPlayer_isRefused_neverWritesRole() {
        // ADT-38: getOfflinePlayer fabricates a deterministic UUID for any string, so a
        // typo must be caught by hasPlayedBefore/isOnline before writing a role row
        // keyed to a UUID nobody owns.
        OfflinePlayer target = mock(OfflinePlayer.class);
        when(target.hasPlayedBefore()).thenReturn(false);
        when(target.isOnline()).thenReturn(false);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getOfflinePlayer("Ghost")).thenReturn(target);
            handler.doUserAdd(sender, "admin", "Ghost");
        }

        verify(mapper, never()).addRole(any(), anyString(), any());
        verify(message).send(sender, "treasuryapi.ui.user.unknown-player", "player", "Ghost");
    }

    @Test
    void doUserAdd_validRole_realPlayer_grantsLowerCasedRoleWithGranterUuid() {
        UUID targetUuid = UUID.randomUUID();
        OfflinePlayer target = mock(OfflinePlayer.class);
        when(target.hasPlayedBefore()).thenReturn(true);
        when(target.getUniqueId()).thenReturn(targetUuid);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getOfflinePlayer("Bob")).thenReturn(target);
            // sender is a Player, so the grant is attributed to their UUID.
            handler.doUserAdd(sender, "ADMIN", "Bob");
        }

        verify(mapper).addRole(targetUuid, "admin", playerId);
        verify(message).send(sender, "treasuryapi.ui.user.added", "role", "admin", "player", "Bob");
    }

    @Test
    void doUserAdd_fromConsole_grantedByIsNull() {
        UUID targetUuid = UUID.randomUUID();
        OfflinePlayer target = mock(OfflinePlayer.class);
        when(target.hasPlayedBefore()).thenReturn(true);
        when(target.getUniqueId()).thenReturn(targetUuid);
        CommandSender console = mock(CommandSender.class); // not a Player

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getOfflinePlayer("Bob")).thenReturn(target);
            handler.doUserAdd(console, "government", "Bob");
        }

        // A console grant carries a null granter uuid (CONSOLE in the audit line).
        verify(mapper).addRole(eq(targetUuid), eq("government"), isNull());
    }

    @Test
    void doUserRemove_removedRow_reportsRemoved() {
        UUID targetUuid = UUID.randomUUID();
        OfflinePlayer target = mock(OfflinePlayer.class);
        when(target.getUniqueId()).thenReturn(targetUuid);
        when(mapper.removeRole(targetUuid, "admin")).thenReturn(1);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getOfflinePlayer("Bob")).thenReturn(target);
            handler.doUserRemove(sender, "ADMIN", "Bob");
        }

        verify(mapper).removeRole(targetUuid, "admin");
        verify(message).send(sender, "treasuryapi.ui.user.removed", "role", "admin", "player", "Bob");
    }

    @Test
    void doUserRemove_noRow_reportsNotGranted() {
        UUID targetUuid = UUID.randomUUID();
        OfflinePlayer target = mock(OfflinePlayer.class);
        when(target.getUniqueId()).thenReturn(targetUuid);
        when(mapper.removeRole(targetUuid, "admin")).thenReturn(0);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getOfflinePlayer("Bob")).thenReturn(target);
            handler.doUserRemove(sender, "admin", "Bob");
        }

        verify(message).send(sender, "treasuryapi.ui.user.not-granted", "role", "admin", "player", "Bob");
    }

    @Test
    void doUserList_withRoles_joinsThem() {
        UUID targetUuid = UUID.randomUUID();
        OfflinePlayer target = mock(OfflinePlayer.class);
        when(target.getUniqueId()).thenReturn(targetUuid);
        when(mapper.listRoles(targetUuid)).thenReturn(List.of("admin", "government"));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getOfflinePlayer("Bob")).thenReturn(target);
            handler.doUserList(sender, "Bob");
        }

        verify(message).send(sender, "treasuryapi.ui.user.list", "player", "Bob", "roles", "admin, government");
    }

    @Test
    void doUserList_noRoles_showsImplicitPlayer() {
        UUID targetUuid = UUID.randomUUID();
        OfflinePlayer target = mock(OfflinePlayer.class);
        when(target.getUniqueId()).thenReturn(targetUuid);
        when(mapper.listRoles(targetUuid)).thenReturn(List.of());

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getOfflinePlayer("Bob")).thenReturn(target);
            handler.doUserList(sender, "Bob");
        }

        verify(message).send(sender, "treasuryapi.ui.user.list",
                "player", "Bob", "roles", "player (no elevated roles)");
    }
}
