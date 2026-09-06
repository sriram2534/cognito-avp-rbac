package com.designpattern.cognitorbac.service;

import com.designpattern.cognitorbac.config.CognitoProperties;
import com.designpattern.cognitorbac.exception.CognitoIntegrationException;
import com.designpattern.cognitorbac.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.TooManyRequestsException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CognitoUserDirectoryTests {
    private static final String USER_SUB = "1f667d9c-2048-4e08-b221-7929924b7a44";

    @Mock private CognitoIdentityProviderClient cognito;

    private CognitoUserDirectory directory;

    @BeforeEach
    void setUp() {
        CognitoProperties properties = new CognitoProperties();
        properties.setUserPoolId("us-east-1_test");
        directory = new CognitoUserDirectory(cognito, properties);
    }

    @Test
    void verifiesDistinctSubjectsWithAnExactCognitoFilter() {
        when(cognito.listUsers(any(ListUsersRequest.class))).thenReturn(ListUsersResponse.builder()
                .users(UserType.builder().username("user@example.com")
                        .attributes(AttributeType.builder().name("sub").value(USER_SUB).build()).build())
                .build());
        when(cognito.adminGetUser(any(AdminGetUserRequest.class))).thenReturn(AdminGetUserResponse.builder()
                .username("user@example.com")
                .userAttributes(AttributeType.builder().name("sub").value(USER_SUB).build())
                .build());

        List<String> result = directory.requireExistingUserSubs(List.of(" " + USER_SUB + " ", USER_SUB));

        assertEquals(List.of(USER_SUB), result);
        ArgumentCaptor<ListUsersRequest> request = ArgumentCaptor.forClass(ListUsersRequest.class);
        verify(cognito).listUsers(request.capture());
        assertEquals("sub = \"" + USER_SUB + "\"", request.getValue().filter());
        assertEquals("us-east-1_test", request.getValue().userPoolId());
        ArgumentCaptor<AdminGetUserRequest> confirmation = ArgumentCaptor.forClass(AdminGetUserRequest.class);
        verify(cognito).adminGetUser(confirmation.capture());
        assertEquals("user@example.com", confirmation.getValue().username());
    }

    @Test
    void rejectsUnknownSubject() {
        when(cognito.listUsers(any(ListUsersRequest.class))).thenReturn(ListUsersResponse.builder().build());

        assertThrows(ResourceNotFoundException.class,
                () -> directory.requireExistingUserSubs(List.of(USER_SUB)));
        verify(cognito, never()).adminGetUser(any(AdminGetUserRequest.class));
    }

    @Test
    void rejectsMalformedSubjectBeforeCallingCognito() {
        assertThrows(IllegalArgumentException.class,
                () -> directory.requireExistingUserSubs(List.of("not-a-cognito-sub")));

        verify(cognito, never()).listUsers(any(ListUsersRequest.class));
    }

    @Test
    void wrapsCognitoFailures() {
        when(cognito.listUsers(any(ListUsersRequest.class))).thenThrow(
                TooManyRequestsException.builder().message("throttled").build());

        assertThrows(CognitoIntegrationException.class,
                () -> directory.requireExistingUserSubs(List.of(USER_SUB)));
    }
}
