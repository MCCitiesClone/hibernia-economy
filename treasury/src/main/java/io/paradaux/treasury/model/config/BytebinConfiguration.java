package io.paradaux.treasury.model.config;

import io.paradaux.hibernia.framework.configurator.annotations.ConfigurationComponent;
import io.paradaux.hibernia.framework.configurator.annotations.ConfigurationValue;
import lombok.Getter;

@ConfigurationComponent
@Getter
public class BytebinConfiguration {

    @ConfigurationValue(path = "bytebin.post-url", defaultValue = "https://pastes.paradaux.io/post")
    private String postUrl;

    @ConfigurationValue(path = "bytebin.base-url", defaultValue = "https://pastes.paradaux.io/")
    private String baseUrl;
}
