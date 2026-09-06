package com.designpattern.cognitorbac.service;

import com.designpattern.cognitorbac.config.CognitoProperties;
import com.designpattern.cognitorbac.exception.CognitoIntegrationException;
import com.designpattern.cognitorbac.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserType;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/** Performs identity-existence checks against the configured Cognito user pool. */
@Service
public class CognitoUserDirectory {
    private static final Logger log = LoggerFactory.getLogger(CognitoUserDirectory.class);
    private static final String SUBJECT_ATTRIBUTE = "sub";
    private static final int MAX_ASSIGNMENT_SIZE = 50;

    private final CognitoIdentityProviderClient cognito;
    private final CognitoProperties properties;

    public CognitoUserDirectory(CognitoIdentityProviderClient cognito, CognitoProperties properties) {
        this.cognito = cognito;
        this.properties = properties;
    }

    public List<String> requireExistingUserSubs(Collection<String> requestedUserSubs) {
        if (requestedUserSubs == null || requestedUserSubs.isEmpty()) {
            throw new IllegalArgumentException("at least one userSub is required");
        }
        if (requestedUserSubs.size() > MAX_ASSIGNMENT_SIZE) {
            throw new IllegalArgumentException("no more than 50 users can be assigned in one request");
        }
        List<String> userSubs = requestedUserSubs.stream()
                .map(CognitoUserDirectory::requireCanonicalSub)
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toCollection(LinkedHashSet::new), List::copyOf));
        userSubs.forEach(this::requireExistingUserSub);
        return userSubs;
    }

    private void requireExistingUserSub(String userSub) {
        try {
            ListUsersResponse response = cognito.listUsers(ListUsersRequest.builder()
                    .userPoolId(properties.getUserPoolId())
                    .filter("sub = \"" + userSub + "\"")
                    .limit(1)
                    .build());
            UserType matchedUser = response.users().stream()
                    .filter(user -> hasSubject(user.attributes(), userSub))
                    .findFirst()
                    .orElseThrow(() -> ResourceNotFoundException.userSub(userSub));
            if (matchedUser.username() == null || matchedUser.username().isBlank()) {
                throw inconsistentIdentity(userSub, "ListUsers returned no username");
            }
            AdminGetUserResponse confirmedUser = cognito.adminGetUser(AdminGetUserRequest.builder()
                    .userPoolId(properties.getUserPoolId())
                    .username(matchedUser.username())
                    .build());
            if (!hasSubject(confirmedUser.userAttributes(), userSub)) {
                throw inconsistentIdentity(userSub, "AdminGetUser returned a different subject");
            }
            log.atDebug().addKeyValue("event", "cognito_user_validated")
                    .addKeyValue("targetUserSub", userSub)
                    .log("Cognito user validated");
        } catch (UserNotFoundException ex) {
            throw ResourceNotFoundException.userSub(userSub);
        } catch (CognitoIdentityProviderException ex) {
            throw new CognitoIntegrationException("validateUserSub",
                    "Cognito operation failed: validateUserSub", ex);
        }
    }

    private static List<AttributeType> attributes(List<AttributeType> attributes) {
        return attributes == null ? List.of() : attributes;
    }

    private static boolean hasSubject(List<AttributeType> attributes, String userSub) {
        return attributes(attributes).stream().anyMatch(attribute -> SUBJECT_ATTRIBUTE.equals(attribute.name())
                && userSub.equals(attribute.value()));
    }

    private static CognitoIntegrationException inconsistentIdentity(String userSub, String reason) {
        return new CognitoIntegrationException("validateUserSub",
                "Cognito returned inconsistent identity data for sub: " + userSub,
                new IllegalStateException(reason));
    }

    private static String requireCanonicalSub(String rawUserSub) {
        if (rawUserSub == null || rawUserSub.isBlank()) {
            throw new IllegalArgumentException("userSub is required");
        }
        String userSub = rawUserSub.trim();
        UUID parsed;
        try {
            parsed = UUID.fromString(userSub);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("userSub must be a canonical UUID: " + userSub, ex);
        }
        if (!parsed.toString().equals(userSub)) {
            throw new IllegalArgumentException("userSub must be a canonical UUID: " + userSub);
        }
        return parsed.toString();
    }
}
