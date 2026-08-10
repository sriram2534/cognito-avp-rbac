package com.designpattern.cognitorbac.service;

import com.designpattern.cognitorbac.avp.GroupNameParser;
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

    /** Resource name for creating/managing a module-admin group (super-admin only). */
    public static final String RESOURCE_MODULE_ADMIN_GROUP = "moduleAdminGroup";

    /** Resource name for creating/managing a resource-level group (module admin within its module). */
    public static final String RESOURCE_GROUP = "resourceGroup";

    /**
     * Authorizes a group-management operation (create/update a Cognito group).
     *
     * <p>The group name is classified into a {@code resourceName}
     * ({@link #RESOURCE_MODULE_ADMIN_GROUP} vs {@link #RESOURCE_GROUP}) and the
     * decision is delegated to AVP as a {@code Write} action on the
     * {@code Portal::Resource} identified by {@code (module, resourceName)}.
     * The caller's group membership is derived by AVP from the identity token;
     * the application never passes caller groups.</p>
     *
     * <p>The ALLOW/DENY decision is made entirely by the Cedar policies: a
     * {@code moduleAdminGroup} is gated to super admins via the forbid policy,
     * while a {@code resourceGroup} is permitted to the matching module admin.
     * Super admins can create both.</p>
     *
     * @param identityToken the caller's raw Cognito ID token JWT
     * @param groupName     the target group name (module:resource:access)
     * @return true if AVP returns ALLOW
     */
    public boolean canManageGroup(String identityToken, String groupName) {
        GroupNameParser target;
        try {
            target = GroupNameParser.parse(groupName);
        } catch (IllegalArgumentException ex) {
            log.warn("Rejecting group-management authorization for malformed group name: {}", groupName);
            return false;
        }

        if (target.isSuperAdmin()) {
            // global:global:admin creation — only super admins can do this.
            // AVP super-admin permit policy (no conditions) allows it;
            // the moduleAdminCreationGuard forbid policy blocks everyone else.
            return isAuthorized(identityToken, "Write", target.module(), RESOURCE_MODULE_ADMIN_GROUP);
        }

        if (target.isModuleAdmin()) {
            // module:global:admin creation — only super admins can do this.
            // AVP moduleAdminCreationGuard forbid policy blocks module admins from creating peers.
            return isAuthorized(identityToken, "Write", target.module(), RESOURCE_MODULE_ADMIN_GROUP);
        }

        // module:resource:access creation — super admins and the owning module admin can do this.
        // AVP module admin permit policy (scoped to resource.module) allows the owning module admin.
        return isAuthorized(identityToken, "Write", target.module(), RESOURCE_GROUP);
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
