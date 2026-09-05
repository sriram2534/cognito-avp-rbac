package com.designpattern.cognitorbac.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CognitoAccessTokenValidatorTests {

    private final CognitoAccessTokenValidator validator = new CognitoAccessTokenValidator();

    @Test
    void acceptsAccessTokenWithAuditIdentity() {
        assertFalse(validator.validate(token("access", "user@nexus.example")).hasErrors());
    }

    @Test
    void rejectsIdToken() {
        assertTrue(validator.validate(token("id", "user@nexus.example")).hasErrors());
    }

    @Test
    void rejectsAccessTokenWithoutEmail() {
        assertTrue(validator.validate(token("access", null)).hasErrors());
    }

    private Jwt token(String tokenUse, String email) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-sub")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("token_use", tokenUse);
        if (email != null) {
            builder.claim("email", email);
        }
        return builder.build();
    }
}
