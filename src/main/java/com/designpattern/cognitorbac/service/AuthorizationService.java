package com.designpattern.cognitorbac.service;

import com.designpattern.cognitorbac.config.VerifiedPermissionsProperties;
import com.designpattern.cognitorbac.dto.avp.AuthorizationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.verifiedpermissions.VerifiedPermissionsClient;
import software.amazon.awssdk.services.verifiedpermissions.model.ActionIdentifier;
import software.amazon.awssdk.services.verifiedpermissions.model.AttributeValue;
import software.amazon.awssdk.services.verifiedpermissions.model.Decision;
import software.amazon.awssdk.services.verifiedpermissions.model.DeterminingPolicyItem;
import software.amazon.awssdk.services.verifiedpermissions.model.EntitiesDefinition;
import software.amazon.awssdk.services.verifiedpermissions.model.EntityIdentifier;
import software.amazon.awssdk.services.verifiedpermissions.model.EntityItem;
import software.amazon.awssdk.services.verifiedpermissions.model.EvaluationErrorItem;
import software.amazon.awssdk.services.verifiedpermissions.model.IsAuthorizedWithTokenRequest;
import software.amazon.awssdk.services.verifiedpermissions.model.IsAuthorizedWithTokenResponse;

import java.util.List;
import java.util.Map;

/**
 * Runtime authorization service that evaluates access decisions against AVP.
 * This is the core service called on every API request to determine ALLOW/DENY.
 */
@Service
public class AuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationService.class);

    private final VerifiedPermissionsClient avpClient;
    private final VerifiedPermissionsProperties properties;

    public AuthorizationService(VerifiedPermissionsClient avpClient,
                                VerifiedPermissionsProperties properties) {
        this.avpClient = avpClient;
        this.properties = properties;
    }

    /**
     * Evaluates whether the given identity token is authorized to perform the
     * specified action on the specified resource.
     *
     * @param identityToken the raw Cognito ID token JWT
     * @param action        the Cedar action (e.g. "Read", "Write", "Admin")
     * @param module        the target resource module (e.g. "ops")
     * @param resourceType  the target resource type (e.g. "store")
     * @return true if authorized, false otherwise
     */
    public boolean isAuthorized(String identityToken, String action, String module, String resourceType) {
        String namespace = properties.getNamespace();
        String resourceEntityId = module + "::" + resourceType;

        try {
            IsAuthorizedWithTokenResponse response = avpClient.isAuthorizedWithToken(
                    IsAuthorizedWithTokenRequest.builder()
                            .policyStoreId(properties.getPolicyStoreId())
                            .identityToken(identityToken)
                            .action(ActionIdentifier.builder()
                                    .actionType(namespace + "::Action")
                                    .actionId(action)
                                    .build())
                            .resource(EntityIdentifier.builder()
                                    .entityType(namespace + "::Resource")
                                    .entityId(resourceEntityId)
                                    .build())
                            .entities(EntitiesDefinition.builder()
                                    .entityList(List.of(
                                            EntityItem.builder()
                                                    .identifier(EntityIdentifier.builder()
                                                            .entityType(namespace + "::Resource")
                                                            .entityId(resourceEntityId)
                                                            .build())
                                                    .attributes(Map.of(
                                                            "module", AttributeValue.builder()
                                                                    .string(module).build(),
                                                            "resourceType", AttributeValue.builder()
                                                                    .string(resourceType).build()
                                                    ))
                                                    .build()
                                    ))
                                    .build())
                            .build());

            boolean allowed = response.decision() == Decision.ALLOW;

            if (!allowed) {
                log.debug("Authorization DENIED: action={}, resource={}, errors={}",
                        action, resourceEntityId, response.errors());
            }

            return allowed;
        } catch (Exception e) {
            log.error("Authorization evaluation failed: action={}, resource={}", action, resourceEntityId, e);
            return false; // Fail-closed: deny on error
        }
    }

    /**
     * Evaluates authorization and returns a detailed response (for debugging/testing).
     */
    public AuthorizationResponse evaluate(String identityToken, String action,
                                           String module, String resourceType) {
        String namespace = properties.getNamespace();
        String resourceEntityId = module + "::" + resourceType;

        try {
            IsAuthorizedWithTokenResponse response = avpClient.isAuthorizedWithToken(
                    IsAuthorizedWithTokenRequest.builder()
                            .policyStoreId(properties.getPolicyStoreId())
                            .identityToken(identityToken)
                            .action(ActionIdentifier.builder()
                                    .actionType(namespace + "::Action")
                                    .actionId(action)
                                    .build())
                            .resource(EntityIdentifier.builder()
                                    .entityType(namespace + "::Resource")
                                    .entityId(resourceEntityId)
                                    .build())
                            .entities(EntitiesDefinition.builder()
                                    .entityList(List.of(
                                            EntityItem.builder()
                                                    .identifier(EntityIdentifier.builder()
                                                            .entityType(namespace + "::Resource")
                                                            .entityId(resourceEntityId)
                                                            .build())
                                                    .attributes(Map.of(
                                                            "module", AttributeValue.builder()
                                                                    .string(module).build(),
                                                            "resourceType", AttributeValue.builder()
                                                                    .string(resourceType).build()
                                                    ))
                                                    .build()
                                    ))
                                    .build())
                            .build());

            List<String> determiningPolicies = response.determiningPolicies().stream()
                    .map(DeterminingPolicyItem::policyId)
                    .toList();

            List<String> errors = response.errors().stream()
                    .map(EvaluationErrorItem::errorDescription)
                    .toList();

            return new AuthorizationResponse(
                    response.decisionAsString(),
                    determiningPolicies,
                    errors
            );
        } catch (Exception e) {
            log.error("Authorization evaluation failed", e);
            return new AuthorizationResponse("DENY", List.of(), List.of(e.getMessage()));
        }
    }

    /**
     * Maps an HTTP method to a Cedar action name.
     */
    public static String httpMethodToAction(String httpMethod) {
        return switch (httpMethod.toUpperCase()) {
            case "GET", "HEAD", "OPTIONS" -> "Read";
            case "POST", "PUT", "PATCH" -> "Write";
            case "DELETE" -> "Admin";
            default -> "Read";
        };
    }
}
