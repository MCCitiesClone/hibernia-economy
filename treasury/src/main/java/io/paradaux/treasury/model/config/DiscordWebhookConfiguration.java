package io.paradaux.treasury.model.config;

import io.paradaux.hibernia.framework.configurator.annotations.ConfigurationComponent;
import io.paradaux.hibernia.framework.configurator.annotations.ConfigurationValue;
import lombok.Getter;

@ConfigurationComponent
@Getter
public class DiscordWebhookConfiguration {

    @ConfigurationValue(path = "tax.webhook.enabled", defaultValue = "false")
    private boolean enabled;

    /** Discord webhook URL. Leave empty to disable even if enabled is true. */
    @ConfigurationValue(path = "tax.webhook.url", defaultValue = "")
    private String url;
}
