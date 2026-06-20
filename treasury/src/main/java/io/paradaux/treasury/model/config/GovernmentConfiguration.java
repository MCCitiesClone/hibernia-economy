package io.paradaux.treasury.model.config;

import io.paradaux.hibernia.framework.configurator.annotations.ConfigurationComponent;
import io.paradaux.hibernia.framework.configurator.annotations.ConfigurationValue;
import lombok.Getter;

@ConfigurationComponent
@Getter
public class GovernmentConfiguration {

    @ConfigurationValue(path = "government.starting-balances-account", defaultValue = "starting-balances")
    private String startingBalancesAccount;

    @ConfigurationValue(path = "government.tax-income-account", defaultValue = "DCGovernment")
    private String taxIncomeAccount;

    @ConfigurationValue(path = "government.fines-account", defaultValue = "GovernmentFines")
    private String finesAccount;
}
