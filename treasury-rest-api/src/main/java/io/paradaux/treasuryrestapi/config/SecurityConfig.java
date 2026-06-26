package io.paradaux.treasuryrestapi.config;

import io.paradaux.treasuryrestapi.security.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Single security chain for the core REST API. Every endpoint
 * ({@code /api/v1/transfers}, {@code /api/v1/auth}, {@code /api/v1/accounts/**},
 * {@code /api/v1/firms/**}) is guarded by the in-game HS256 {@link JwtAuthFilter},
 * which authenticates an API-key JWT into a {@code VerifiedToken} principal.
 *
 * <p>The explorer UI and its Keycloak OIDC chain used to live here too; that
 * surface now belongs to the standalone {@code economy-explorer} project, which
 * reads the shared database directly.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain apiChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public, read-only ChestShop market data — no JWT (JwtAuthFilter
                        // skips this prefix; throttled by IP via anonymousPerMinute).
                        .requestMatchers("/api/v1/chestshop/**").permitAll()
                        .requestMatchers("/api/v1/transfers/**", "/api/v1/auth/**",
                                "/api/v1/accounts/**", "/api/v1/firms/**",
                                "/api/v1/webhooks/**", "/api/v1/admin/**").authenticated()
                        .anyRequest().permitAll())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable);
        return http.build();
    }
}
