package io.paradaux.treasury.commands;

import io.paradaux.hibernia.framework.i18n.Message;
import io.paradaux.treasury.model.economy.TransactionEntry;
import io.paradaux.treasury.services.AccountService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Renders a {@link TransactionEntry} as a {@code treasury.transactions.entry}
 * line — signed, coloured amount; memo (with {@code message} fallback
 * and an em-dash when absent); settlement time — shared by {@code /transactions}
 * and {@code /gov account history} so the two stay identical.
 *
 * <p>The amount is passed as a {@link Component} rather than a MiniMessage string:
 * {@link Message} inserts plain-String placeholder values as inert literal text, so
 * hand-built {@code <green>…</green>} markup would render as visible tags. The memo
 * needs no escaping for the same reason — it is inert already.</p>
 */
final class TransactionEntryRenderer {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("MM/dd HH:mm").withZone(ZoneId.systemDefault());

    private TransactionEntryRenderer() {
    }

    static void send(CommandSender recipient, Message message,
                     AccountService accountService, TransactionEntry entry) {
        boolean credit = entry.getAmount().signum() >= 0;
        String formattedAmount = accountService.formatAmount(entry.getAmount().abs());
        Component coloredAmount = Component.text((credit ? "+" : "-") + formattedAmount,
                credit ? NamedTextColor.GREEN : NamedTextColor.RED);
        String memo = entry.getMemo() != null ? entry.getMemo() : entry.getMessage();
        if (memo == null) memo = "—";
        String time = entry.getSettlementTime() != null
                ? TIME_FMT.format(entry.getSettlementTime()) : "—";

        message.send(recipient, "treasury.transactions.entry",
                "txn", String.valueOf(entry.getTxnId()),
                "amount", coloredAmount,
                "memo", memo,
                "time", time);
    }
}
