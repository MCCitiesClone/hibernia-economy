package io.paradaux.treasuryapi.model.config;

import io.paradaux.hibernia.framework.configurator.annotations.ConfigurationComponent;
import io.paradaux.hibernia.framework.configurator.annotations.ConfigurationValue;
import lombok.Getter;

/**
 * Keycloak Admin API access for the in-game account-linking flow
 * (/treasuryapi ui link). The plugin uses its service-account client to set the
 * player's {@code minecraft_uuid} attribute on their Keycloak user. Disabled by
 * default; enable + supply the secret per server (never commit a real secret).
 */
@ConfigurationComponent
@Getter
public class KeycloakConfiguration {

    @ConfigurationValue(path = "keycloak.enabled", defaultValue = "false")
    private boolean enabled;

    @ConfigurationValue(path = "keycloak.base-url", defaultValue = "https://auth.paradaux.io")
    private String baseUrl;

    @ConfigurationValue(path = "keycloak.realm", defaultValue = "minecraft")
    private String realm;

    @ConfigurationValue(path = "keycloak.client-id", defaultValue = "treasury-api-plugin")
    private String clientId;

    @ConfigurationValue(path = "keycloak.client-secret", defaultValue = "")
    private String clientSecret;
}
