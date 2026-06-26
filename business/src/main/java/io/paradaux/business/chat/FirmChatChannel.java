package io.paradaux.business.chat;

import net.draycia.carbon.api.channels.ChannelPermissionResult;
import net.draycia.carbon.api.channels.ChannelPermissions;
import net.draycia.carbon.api.channels.ChatChannel;
import net.draycia.carbon.api.users.CarbonPlayer;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.List;

/**
 * A single dynamically-scoped CarbonChat channel for firm chat (PAR-20). Carbon
 * iterates every registered channel per message, so we register ONE channel
 * whose {@link #recipients(CarbonPlayer)} resolves the sender's active firm at
 * send time — firm membership stays in {@link FirmChatService}, never in Carbon's
 * registry (the same pattern Carbon's own Factions/Towny integrations use).
 *
 * <p>Verified against carbonchat-api 3.0.0-beta.32. The 3.0.0-beta API line
 * churns across betas — pinned in build.gradle.kts.
 */
public class FirmChatChannel implements ChatChannel {

    static final Key KEY = Key.key("business", "firmchat");

    private final FirmChatService service;
    private final ChannelPermissions permissions;

    public FirmChatChannel(FirmChatService service) {
        this.service = service;
        // Dynamic: re-evaluated per context. A player may speak/hear only while
        // they have an active firm selected (via /firm chat).
        this.permissions = ChannelPermissions.uniformDynamic(player ->
                service.activeFirm(player.uuid()).isPresent()
                        ? ChannelPermissionResult.allowed()
                        : ChannelPermissionResult.denied(
                                Component.text("Join firm chat first with /firm chat <firm>.")));
    }

    @Override
    public Key key() {
        return KEY;
    }

    @Override
    public ChannelPermissions permissions() {
        return permissions;
    }

    @Override
    public List<Audience> recipients(CarbonPlayer sender) {
        return service.recipients(sender.uuid());
    }

    @Override
    public Component render(CarbonPlayer sender, Audience recipient, Component message, Component originalMessage) {
        return Component.text("[Firm] ", NamedTextColor.GREEN)
                .append(sender.displayName())
                .append(Component.text(": ", NamedTextColor.GRAY))
                .append(message);
    }

    @Override
    public String quickPrefix() {
        // MUST be null, not "" — Carbon's channelForMessage skips null-prefix channels, but a
        // non-null "" matches every message via startsWith("") and then strips it with
        // replaceText(matchLiteral("")), blanking the body. Firm chat is entered via /firm chat,
        // so it has no quick-prefix at all. (PAR-20)
        return null;
    }

    @Override
    public boolean shouldRegisterCommands() {
        return false; // driven by /firm chat, not a Carbon-registered command
    }

    @Override
    public String commandName() {
        return "firmchat";
    }

    @Override
    public List<String> commandAliases() {
        return List.of();
    }

    @Override
    public double radius() {
        return -1; // org-wide, not proximity-based
    }

    @Override
    public boolean emptyRadiusRecipientsMessage() {
        return false;
    }

    @Override
    public long cooldown() {
        return 0;
    }

    @Override
    public long playerCooldown(CarbonPlayer player) {
        return 0;
    }

    @Override
    public long startCooldown(CarbonPlayer player) {
        return 0;
    }

    @Override
    public boolean shouldCrossServer() {
        return false;
    }
}
