package com.designpattern.cognitorbac.service;

import com.designpattern.cognitorbac.avp.CedarPolicyBuilder;
import com.designpattern.cognitorbac.avp.GroupNameParser;
import com.designpattern.cognitorbac.config.VerifiedPermissionsProperties;
import com.designpattern.cognitorbac.dto.avp.CreatePolicyRequest;
import com.designpattern.cognitorbac.dto.avp.PolicyResponse;
import com.designpattern.cognitorbac.dto.avp.UpdatePolicyRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.verifiedpermissions.VerifiedPermissionsClient;
import software.amazon.awssdk.services.verifiedpermissions.model.CreatePolicyResponse;
import software.amazon.awssdk.services.verifiedpermissions.model.DeletePolicyRequest;
import software.amazon.awssdk.services.verifiedpermissions.model.GetPolicyRequest;
import software.amazon.awssdk.services.verifiedpermissions.model.GetPolicyResponse;
import software.amazon.awssdk.services.verifiedpermissions.model.ListPoliciesRequest;
import software.amazon.awssdk.services.verifiedpermissions.model.ListPoliciesResponse;
import software.amazon.awssdk.services.verifiedpermissions.model.PolicyDefinition;
import software.amazon.awssdk.services.verifiedpermissions.model.PolicyItem;
import software.amazon.awssdk.services.verifiedpermissions.model.StaticPolicyDefinition;
import software.amazon.awssdk.services.verifiedpermissions.model.UpdatePolicyResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages Cedar policies in AWS Verified Permissions.
 * Enforces that:
 * - Only super admins can create module admin policies
 * - Module admins can only create policies within their own module
 * - No module admin can create another module admin
 */
@Service
public class PolicyService {

    private static final Logger log = LoggerFactory.getLogger(PolicyService.class);

    private final VerifiedPermissionsClient avpClient;
    private final VerifiedPermissionsProperties avpProperties;

    public PolicyService(VerifiedPermissionsClient avpClient,
                         VerifiedPermissionsProperties avpProperties) {
        this.avpClient = avpClient;
        this.avpProperties = avpProperties;
    }

    /**
     * Creates a policy based on the group name convention.
     * Automatically generates the correct Cedar statement based on the group structure.
     *
     * @param request      the policy creation request
     * @param callerGroups the groups of the authenticated caller (for authorization)
     */
    public PolicyResponse createPolicy(CreatePolicyRequest request, List<String> callerGroups) {
        GroupNameParser target = GroupNameParser.parse(request.groupName());
        String namespace = avpProperties.getNamespace();

        // Authorization checks
        validateCallerCanCreatePolicy(target, callerGroups);

        // Generate the Cedar policy statement
        String statement = generatePolicyStatement(request, target, namespace);
        String description = request.description() != null
                ? request.description()
                : "Policy for group: " + request.groupName();

        // Create in AVP
        CreatePolicyResponse response = avpClient.createPolicy(
                software.amazon.awssdk.services.verifiedpermissions.model.CreatePolicyRequest.builder()
                        .policyStoreId(avpProperties.getPolicyStoreId())
                        .definition(PolicyDefinition.fromStaticValue(
                                StaticPolicyDefinition.builder()
                                        .statement(statement)
                                        .description(description)
                                        .build()))
                        .build());

        log.info("Created policy [{}] for group [{}]", response.policyId(), request.groupName());

        return new PolicyResponse(
                response.policyId(),
                "STATIC",
                statement,
                description,
                response.createdDate(),
                response.lastUpdatedDate()
        );
    }

    /**
     * Creates a module admin policy (with corresponding forbid policy).
     * Only callable by super admins.
     */
    public List<PolicyResponse> createModuleAdminPolicies(String moduleName, List<String> callerGroups) {
        if (!isSuperAdmin(callerGroups)) {
            throw new AccessDeniedException("Only super admins can create module admin policies");
        }

        String namespace = avpProperties.getNamespace();
        String groupName = moduleName + ":global:admin";
        List<PolicyResponse> results = new ArrayList<>();

        // Permit policy: all actions within module
        String permitStatement = CedarPolicyBuilder.moduleAdminPolicy(namespace, groupName, moduleName);
        CreatePolicyResponse permitResponse = avpClient.createPolicy(
                software.amazon.awssdk.services.verifiedpermissions.model.CreatePolicyRequest.builder()
                        .policyStoreId(avpProperties.getPolicyStoreId())
                        .definition(PolicyDefinition.fromStaticValue(
                                StaticPolicyDefinition.builder()
                                        .statement(permitStatement)
                                        .description("Module admin permit: " + groupName)
                                        .build()))
                        .build());

        results.add(new PolicyResponse(
                permitResponse.policyId(), "STATIC", permitStatement,
                "Module admin permit: " + groupName,
                permitResponse.createdDate(), permitResponse.lastUpdatedDate()));

        // Forbid policy: cannot access other modules
        String forbidStatement = CedarPolicyBuilder.moduleAdminForbidPolicy(namespace, groupName, moduleName);
        CreatePolicyResponse forbidResponse = avpClient.createPolicy(
                software.amazon.awssdk.services.verifiedpermissions.model.CreatePolicyRequest.builder()
                        .policyStoreId(avpProperties.getPolicyStoreId())
                        .definition(PolicyDefinition.fromStaticValue(
                                StaticPolicyDefinition.builder()
                                        .statement(forbidStatement)
                                        .description("Module admin forbid outside module: " + groupName)
                                        .build()))
                        .build());

        results.add(new PolicyResponse(
                forbidResponse.policyId(), "STATIC", forbidStatement,
                "Module admin forbid outside module: " + groupName,
                forbidResponse.createdDate(), forbidResponse.lastUpdatedDate()));

        log.info("Created module admin policies for module [{}]", moduleName);
        return results;
    }

    /**
     * Updates an existing policy's Cedar statement.
     */
    public PolicyResponse updatePolicy(UpdatePolicyRequest request, List<String> callerGroups) {
        // Verify the caller has access (at minimum must be a module admin or super admin)
        if (!isSuperAdmin(callerGroups) && !isAnyModuleAdmin(callerGroups)) {
            throw new AccessDeniedException("Only admins can update policies");
        }

        UpdatePolicyResponse response = avpClient.updatePolicy(
                software.amazon.awssdk.services.verifiedpermissions.model.UpdatePolicyRequest.builder()
                        .policyStoreId(avpProperties.getPolicyStoreId())
                        .policyId(request.policyId())
                        .definition(software.amazon.awssdk.services.verifiedpermissions.model.UpdatePolicyDefinition.fromStaticValue(
                                software.amazon.awssdk.services.verifiedpermissions.model.UpdateStaticPolicyDefinition.builder()
                                        .statement(request.statement())
                                        .description(request.description())
                                        .build()))
                        .build());

        log.info("Updated policy [{}]", request.policyId());

        return new PolicyResponse(
                response.policyId(),
                "STATIC",
                request.statement(),
                request.description(),
                response.createdDate(),
                response.lastUpdatedDate()
        );
    }

    /**
     * Retrieves a single policy by ID.
     */
    public PolicyResponse getPolicy(String policyId) {
        GetPolicyResponse response = avpClient.getPolicy(GetPolicyRequest.builder()
                .policyStoreId(avpProperties.getPolicyStoreId())
                .policyId(policyId)
                .build());

        String statement = response.definition().staticValue() != null
                ? response.definition().staticValue().statement()
                : "";
        String description = response.definition().staticValue() != null
                ? response.definition().staticValue().description()
                : "";

        return new PolicyResponse(
                response.policyId(),
                response.policyTypeAsString(),
                statement,
                description,
                response.createdDate(),
                response.lastUpdatedDate()
        );
    }

    /**
     * Lists all policies in the policy store.
     */
    public List<PolicyResponse> listPolicies() {
        List<PolicyResponse> results = new ArrayList<>();
        String nextToken = null;

        do {
            ListPoliciesRequest.Builder requestBuilder = ListPoliciesRequest.builder()
                    .policyStoreId(avpProperties.getPolicyStoreId())
                    .maxResults(50);
            if (nextToken != null) {
                requestBuilder.nextToken(nextToken);
            }

            ListPoliciesResponse response = avpClient.listPolicies(requestBuilder.build());

            for (PolicyItem item : response.policies()) {
                results.add(new PolicyResponse(
                        item.policyId(),
                        item.policyTypeAsString(),
                        null, // statement not included in list response
                        item.definition().staticValue() != null ? item.definition().staticValue().description() : null,
                        item.createdDate(),
                        item.lastUpdatedDate()
                ));
            }

            nextToken = response.nextToken();
        } while (nextToken != null);

        return results;
    }

    /**
     * Deletes a policy by ID. Only super admins can delete policies.
     */
    public void deletePolicy(String policyId, List<String> callerGroups) {
        if (!isSuperAdmin(callerGroups)) {
            throw new AccessDeniedException("Only super admins can delete policies");
        }

        avpClient.deletePolicy(DeletePolicyRequest.builder()
                .policyStoreId(avpProperties.getPolicyStoreId())
                .policyId(policyId)
                .build());

        log.info("Deleted policy [{}]", policyId);
    }

    /**
     * Auto-generates the correct policy when a new Cognito group is created.
     * Call this from GroupService after creating a group in Cognito.
     */
    public PolicyResponse createPolicyForGroup(String groupName, List<String> callerGroups) {
        GroupNameParser parsed = GroupNameParser.parse(groupName);
        String action = parsed.cedarAction();

        CreatePolicyRequest request = new CreatePolicyRequest(
                groupName, action, parsed.module(), parsed.resource(),
                "Auto-generated policy for group: " + groupName
        );

        return createPolicy(request, callerGroups);
    }

    // ─── Private helpers ────────────────────────────────────────────────────────

    private void validateCallerCanCreatePolicy(GroupNameParser target, List<String> callerGroups) {
        // Rule 1: Only super admin can create module admin groups
        if (target.isModuleAdmin() && !target.isSuperAdmin()) {
            if (!isSuperAdmin(callerGroups)) {
                throw new AccessDeniedException(
                        "Only super admins can create module admin policies. " +
                                "Module admins cannot create other module admins.");
            }
        }

        // Rule 2: Module admins can only create policies within their own module
        if (target.isResourceLevel()) {
            boolean callerIsSuper = isSuperAdmin(callerGroups);
            boolean callerIsModuleAdmin = isModuleAdmin(callerGroups, target.module());

            if (!callerIsSuper && !callerIsModuleAdmin) {
                throw new AccessDeniedException(
                        "You must be a super admin or module admin of '" + target.module() +
                                "' to create policies for this module.");
            }
        }
    }

    private String generatePolicyStatement(CreatePolicyRequest request,
                                            GroupNameParser target,
                                            String namespace) {
        if (target.isSuperAdmin()) {
            return CedarPolicyBuilder.superAdminPolicy(namespace, request.groupName());
        }

        if (target.isModuleAdmin()) {
            return CedarPolicyBuilder.moduleAdminPolicy(namespace, request.groupName(), target.module());
        }

        // Resource-level policy
        if ("global".equals(request.resourceType())) {
            return CedarPolicyBuilder.moduleWidePolicy(
                    namespace, request.groupName(), request.action(), request.module());
        }

        return CedarPolicyBuilder.resourcePolicy(
                namespace, request.groupName(), request.action(),
                request.module(), request.resourceType());
    }

    private boolean isSuperAdmin(List<String> groups) {
        return groups != null && groups.contains(avpProperties.getSuperAdminGroup());
    }

    private boolean isModuleAdmin(List<String> groups, String module) {
        if (groups == null) return false;
        String expectedGroup = module + ":global:admin";
        return groups.contains(expectedGroup);
    }

    private boolean isAnyModuleAdmin(List<String> groups) {
        if (groups == null) return false;
        return groups.stream().anyMatch(g -> {
            try {
                GroupNameParser parsed = GroupNameParser.parse(g);
                return parsed.isModuleAdmin();
            } catch (IllegalArgumentException e) {
                return false;
            }
        });
    }
}
