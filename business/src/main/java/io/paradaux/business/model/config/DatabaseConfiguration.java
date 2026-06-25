package io.paradaux.business.model.config;

import io.paradaux.hibernia.framework.configurator.annotations.ConfigurationComponent;
import io.paradaux.hibernia.framework.configurator.annotations.ConfigurationValue;
import lombok.Getter;


@ConfigurationComponent
@Getter
public class DatabaseConfiguration {

    @ConfigurationValue(path = "database.host", defaultValue = "localhost")
    private String host;

    @ConfigurationValue(path = "database.port", defaultValue = "3306")
    private String port;

    @ConfigurationValue(path = "database.database", defaultValue = "treasury")
    private String database;

    @ConfigurationValue(path = "database.username", defaultValue = "root")
    private String username;

    @ConfigurationValue(path = "database.password", defaultValue = "password")
    private String password;

    @ConfigurationValue(path = "database.table-prefix", defaultValue = "treasury_")
    private String tablePrefix;
}
