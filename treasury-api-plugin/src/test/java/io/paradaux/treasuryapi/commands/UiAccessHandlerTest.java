package io.paradaux.treasuryapi.commands;

import io.paradaux.hibernia.framework.i18n.Message;
import io.paradaux.treasuryapi.TreasuryAPI;
import io.paradaux.treasuryapi.mappers.ExplorerUiMapper;
import io.paradaux.treasuryapi.services.KeycloakAdminClient;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
}
