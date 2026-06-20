package io.paradaux.business.commands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.paradaux.hibernia.framework.commander.annotations.*;
import io.paradaux.hibernia.framework.commander.spi.CommandHandler;
import io.paradaux.hibernia.framework.i18n.Message;
import io.paradaux.business.model.Firm;
import io.paradaux.business.model.config.BalanceTaxConfiguration;
import io.paradaux.business.services.FirmPropertyService;
import io.paradaux.business.services.FirmService;
import io.paradaux.business.services.FirmTransactionService;
import io.paradaux.business.utils.resolvers.FirmName;
import io.paradaux.treasury.api.TreasuryApi;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Singleton
@Command({"db", "democracybusiness", "business", "firm", "company"})
public class TaxCommands implements CommandHandler {

    private static final String EXEMPT_KEY = "balance-tax.exempt";

    private final FirmService firmService;
    private final FirmPropertyService firmPropertyService;
    private final FirmTransactionService firmTransactionService;
    private final BalanceTaxConfiguration taxConfig;
    private final TreasuryApi treasuryApi;
    private final Message message;

    @Inject
    public TaxCommands(FirmService firmService,
                       FirmPropertyService firmPropertyService,
                       FirmTransactionService firmTransactionService,
                       BalanceTaxConfiguration taxConfig,
                       TreasuryApi treasuryApi,
                       Message message) {
        this.firmService = firmService;
        this.firmPropertyService = firmPropertyService;
        this.firmTransactionService = firmTransactionService;
        this.taxConfig = taxConfig;
        this.treasuryApi = treasuryApi;
        this.message = message;
    }

    @Route("tax exempt <firm> <exempt>")
    @Permission("business.tax.exempt")
    @Async
    @Description("Set or clear the balance-tax exemption for a firm")
    public void setExempt(@Sender Player sender, @Arg("firm") FirmName firmRef, @Arg("exempt") String exempt) {
        String firm = firmRef.value();
        Firm f = firmService.getFirmByNameOrId(firm);
        if (f == null) {
            message.send(sender, "business.firm.not-found", "firm", firm);
            return;
        }

        Boolean parsed = parseBoolean(exempt);
        if (parsed == null) {
            message.send(sender, "business.tax.exempt.invalid", "value", exempt);
            return;
        }

        firmPropertyService.setBoolean(f.getFirmId(), EXEMPT_KEY, parsed);
        message.send(sender, "business.tax.exempt.success",
            "firm", f.getDisplayName(),
            "exempt", parsed ? "exempt" : "not exempt");
    }

    @Route("tax info <firm>")
    @Permission("business.tax.info")
    @Async
    @Description("Show tax status and estimated weekly tax for a firm")
    public void taxInfo(@Sender Player sender, @Arg("firm") FirmName firmRef) {
        String firm = firmRef.value();
        Firm f = firmService.getFirmByNameOrId(firm);
        if (f == null) {
            message.send(sender, "business.firm.not-found", "firm", firm);
            return;
        }

        boolean exempt = firmPropertyService.getBoolean(f.getFirmId(), EXEMPT_KEY).orElse(false);
        BigDecimal totalBalance = firmTransactionService.getAggregateBalance(f.getFirmId());
        BigDecimal rate = taxConfig.getWeeklyRate(totalBalance);
        BigDecimal estimatedTax = totalBalance.multiply(rate).setScale(2, RoundingMode.HALF_UP);

        String balanceFmt = treasuryApi.formatAmount(totalBalance);
        String taxFmt = treasuryApi.formatAmount(estimatedTax);
        String ratePct = rate.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "%";
        String exemptStatus = exempt ? "<green>Exempt</green>" : "<red>Not Exempt</red>";

        message.send(sender, "business.tax.info",
            "firm", f.getDisplayName(),
            "exempt", exemptStatus,
            "balance", balanceFmt,
            "rate", ratePct,
            "estimatedTax", taxFmt);
    }

    private static Boolean parseBoolean(String token) {
        if (token == null) return null;
        String t = token.trim().toLowerCase();
        return switch (t) {
            case "true", "t", "yes", "y", "1", "on", "enable", "enabled" -> Boolean.TRUE;
            case "false", "f", "no", "n", "0", "off", "disable", "disabled" -> Boolean.FALSE;
            default -> null;
        };
    }
}
