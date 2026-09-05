package com.designpattern.cognitorbac.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/** Requires a Cognito access token with the identity fields needed by auditing. */
@Component
public class CognitoAccessTokenValidator implements OAuth2TokenValidator<Jwt> {

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        boolean accessToken = "access".equals(jwt.getClaimAsString("token_use"));
        String userSub = jwt.getSubject();
        String userEmail = jwt.getClaimAsString("email");
        if (accessToken && userSub != null && !userSub.isBlank()
                && userEmail != null && !userEmail.isBlank()) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                "invalid_token",
                "Cognito access token must contain token_use=access, sub, and email",
                null));
    }
}
