package io.paradaux.treasury.model.config;

import io.paradaux.hibernia.framework.configurator.annotations.ConfigurationComponent;
import io.paradaux.hibernia.framework.configurator.annotations.ConfigurationValue;
import lombok.Getter;

@ConfigurationComponent
@Getter
public class LoggingConfiguration {

    @ConfigurationValue(path = "logging.level", defaultValue = "WARN")
    private String level;
}
