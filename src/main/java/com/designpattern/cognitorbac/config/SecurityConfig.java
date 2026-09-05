package com.designpattern.cognitorbac.config;

import com.designpattern.cognitorbac.security.CognitoAccessTokenValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.List;

/**
 * Configures the service as an OAuth2 Resource Server that validates
 * Cognito-issued JWT access tokens.
 *
 * <ul>
 *   <li>Stateless session management (no server-side sessions).</li>
 *   <li>Signature validation against the Cognito JWKS endpoint.</li>
 *   <li>Issuer, expiry, audience, access-token, and audit-identity validation.</li>
 *   <li>Authorization is enforced by the external authorization service or API gateway.</li>
 * </ul>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final CognitoProperties properties;
    private final CognitoAccessTokenValidator accessTokenValidator;

    public SecurityConfig(CognitoProperties properties, CognitoAccessTokenValidator accessTokenValidator) {
        this.properties = properties;
        this.accessTokenValidator = accessTokenValidator;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri(properties.jwkSetUri())
                .build();

        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(JwtValidators.createDefaultWithIssuer(properties.resolveIssuerUri()));
        validators.add(audienceValidator(properties.getAppClientId()));
        validators.add(accessTokenValidator);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
        return decoder;
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> List.of());
        converter.setPrincipalClaimName("username");
        return converter;
    }

    /**
     * Cognito access tokens do not always carry a standard {@code aud} claim;
     * the App Client ID is exposed via {@code client_id}. This validator accepts
     * either location.
     */
    private OAuth2TokenValidator<Jwt> audienceValidator(String expectedClientId) {
        return jwt -> {
            List<String> audiences = jwt.getAudience();
            String clientId = jwt.getClaimAsString("client_id");
            boolean valid = (audiences != null && audiences.contains(expectedClientId))
                    || expectedClientId.equals(clientId);
            if (valid) {
                return org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.success();
            }
            return org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.failure(
                    new org.springframework.security.oauth2.core.OAuth2Error(
                            "invalid_token",
                            "The required audience/client_id (%s) is missing".formatted(expectedClientId),
                            null));
        };
    }

}
