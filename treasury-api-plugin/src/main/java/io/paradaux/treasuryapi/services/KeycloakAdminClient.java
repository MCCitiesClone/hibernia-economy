package io.paradaux.treasuryapi.services;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.paradaux.treasuryapi.model.config.KeycloakConfiguration;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/**
 * Thin Keycloak Admin REST client for the in-game linking flow: authenticates
 * with the plugin's service-account client and sets a user's
 * {@code minecraft_uuid} (+ {@code minecraft_name}) attribute. Uses the JDK
 * HTTP client and Paper-provided Gson — no extra dependencies.
 */
@Singleton
public class KeycloakAdminClient {

    private final KeycloakConfiguration cfg;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Inject
    public KeycloakAdminClient(KeycloakConfiguration cfg) {
        this.cfg = cfg;
    }

    public boolean isEnabled() {
        return cfg.isEnabled();
    }

    /** Merges the minecraft attributes into the Keycloak user identified by {@code sub}. */
    public void setMinecraftAttributes(String sub, UUID uuid, String name) throws Exception {
        String token = serviceAccountToken();
        String userUrl = cfg.getBaseUrl() + "/admin/realms/" + cfg.getRealm() + "/users/" + sub;

        HttpResponse<String> get = http.send(
                HttpRequest.newBuilder(URI.create(userUrl))
                        .timeout(Duration.ofSeconds(15))
                        .header("Authorization", "Bearer " + token)
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        if (get.statusCode() / 100 != 2) {
            throw new IllegalStateException("Keycloak GET user " + sub + " failed: HTTP " + get.statusCode());
        }

        JsonObject user = JsonParser.parseString(get.body()).getAsJsonObject();
        JsonElement attrsEl = user.get("attributes");
        JsonObject attrs = (attrsEl != null && attrsEl.isJsonObject()) ? attrsEl.getAsJsonObject() : new JsonObject();
        attrs.add("minecraft_uuid", single(uuid.toString()));
        if (name != null) attrs.add("minecraft_name", single(name));
        user.add("attributes", attrs);

        HttpResponse<String> put = http.send(
                HttpRequest.newBuilder(URI.create(userUrl))
                        .timeout(Duration.ofSeconds(15))
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(user.toString()))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        if (put.statusCode() / 100 != 2) {
            throw new IllegalStateException("Keycloak PUT user " + sub + " failed: HTTP " + put.statusCode());
        }
    }

    private String serviceAccountToken() throws Exception {
        String tokenUrl = cfg.getBaseUrl() + "/realms/" + cfg.getRealm() + "/protocol/openid-connect/token";
        String form = "grant_type=client_credentials"
                + "&client_id=" + enc(cfg.getClientId())
                + "&client_secret=" + enc(cfg.getClientSecret());
        HttpResponse<String> res = http.send(
                HttpRequest.newBuilder(URI.create(tokenUrl))
                        .timeout(Duration.ofSeconds(15))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(form))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) {
            throw new IllegalStateException("Keycloak token request failed: HTTP " + res.statusCode());
        }
        return JsonParser.parseString(res.body()).getAsJsonObject().get("access_token").getAsString();
    }

    private static JsonArray single(String value) {
        JsonArray a = new JsonArray();
        a.add(value);
        return a;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }
}
