package com.designpattern.cognitorbac.controller;

import com.designpattern.cognitorbac.avp.SecurityContextHelper;
import com.designpattern.cognitorbac.dto.avp.AuthorizationRequest;
import com.designpattern.cognitorbac.dto.avp.AuthorizationResponse;
import com.designpattern.cognitorbac.dto.avp.CreatePolicyRequest;
import com.designpattern.cognitorbac.dto.avp.PolicyResponse;
import com.designpattern.cognitorbac.dto.avp.UpdatePolicyRequest;
import com.designpattern.cognitorbac.service.AuthorizationService;
import com.designpattern.cognitorbac.service.PolicyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST API for managing Cedar policies in AWS Verified Permissions.
 *
 * <p>Access rules:
 * <ul>
 *   <li>Super admin ({@code global:global:admin}) — can create/update/delete any policy,
 *       including module admin policies</li>
 *   <li>Module admin ({@code module:global:admin}) — can create/update resource-level policies
 *       within their module only. Cannot create other module admins.</li>
 *   <li>All other users — read-only access to list/view policies.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/policies")
@PreAuthorize("isAuthenticated()")
public class PolicyController {

    private final PolicyService policyService;
    private final AuthorizationService authorizationService;
    private final SecurityContextHelper securityContextHelper;

    public PolicyController(PolicyService policyService,
                            AuthorizationService authorizationService,
                            SecurityContextHelper securityContextHelper) {
        this.policyService = policyService;
        this.authorizationService = authorizationService;
        this.securityContextHelper = securityContextHelper;
    }

    /**
     * Lists all policies in the policy store.
     */
    @GetMapping
    public List<PolicyResponse> listPolicies() {
        return policyService.listPolicies();
    }

    /**
     * Gets a single policy by ID with full Cedar statement.
     */
    @GetMapping("/{policyId}")
    public ResponseEntity<PolicyResponse> getPolicy(@PathVariable String policyId) {
        return ResponseEntity.ok(policyService.getPolicy(policyId));
    }

    /**
     * Creates a new policy. The Cedar statement is auto-generated based on the
     * group name convention (module:resource:access).
     *
     * <p>Authorization:
     * - Super admin can create any policy
     * - Module admin can create resource-level policies within their module
     * - Module admins CANNOT create module admin policies</p>
     */
    @PostMapping
    public ResponseEntity<PolicyResponse> createPolicy(@Valid @RequestBody CreatePolicyRequest request) {
        authorizeGroupManagement(request.groupName());
        List<String> callerGroups = securityContextHelper.getCallerGroups();
        PolicyResponse response = policyService.createPolicy(request, callerGroups);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Creates module admin policies (permit + forbid pair).
     * Only super admins can call this endpoint.
     *
     * @param moduleName the module name (e.g. "ops", "banners", "subscription")
     */
    @PostMapping("/module-admin/{moduleName}")
    public ResponseEntity<List<PolicyResponse>> createModuleAdminPolicies(
            @PathVariable String moduleName) {
        List<String> callerGroups = securityContextHelper.getCallerGroups();
        List<PolicyResponse> responses = policyService.createModuleAdminPolicies(moduleName, callerGroups);
        return ResponseEntity.status(HttpStatus.CREATED).body(responses);
    }

    /**
     * Updates an existing policy's Cedar statement.
     */
    @PutMapping
    public ResponseEntity<PolicyResponse> updatePolicy(@Valid @RequestBody UpdatePolicyRequest request) {
        List<String> callerGroups = securityContextHelper.getCallerGroups();
        PolicyResponse response = policyService.updatePolicy(request, callerGroups);
        return ResponseEntity.ok(response);
    }

    /**
     * Deletes a policy by ID. Only super admins can delete policies.
     */
    @DeleteMapping("/{policyId}")
    public ResponseEntity<Void> deletePolicy(@PathVariable String policyId) {
        List<String> callerGroups = securityContextHelper.getCallerGroups();
        policyService.deletePolicy(policyId, callerGroups);
        return ResponseEntity.noContent().build();
    }

    /**
     * Auto-creates a policy for a newly created Cognito group.
     * Called after a group is created to automatically attach the correct policy.
     *
     * @param groupName the full group name (e.g. "ops:store:write")
     */
    @PostMapping("/auto-create")
    public ResponseEntity<PolicyResponse> autoCreatePolicyForGroup(
            @RequestParam String groupName) {
        authorizeGroupManagement(groupName);
        List<String> callerGroups = securityContextHelper.getCallerGroups();
        PolicyResponse response = policyService.createPolicyForGroup(groupName, callerGroups);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Authorizes a group-management operation via AVP. The Cedar policies decide
     * whether the caller may create/manage the target group: module-admin groups
     * are restricted to super admins, resource groups to the owning module admin.
     *
     * @throws AccessDeniedException if AVP returns DENY
     */
    private void authorizeGroupManagement(String groupName) {
        String identityToken = securityContextHelper.getIdentityToken();
        if (identityToken == null || !authorizationService.canManageGroup(identityToken, groupName)) {
            throw new AccessDeniedException("Not authorized to manage group: " + groupName);
        }
    }

    /**
     * Evaluates an authorization decision (for testing/debugging).
     * Returns detailed information about which policies determined the decision.
     */
    @PostMapping("/evaluate")
    public ResponseEntity<AuthorizationResponse> evaluate(
            @Valid @RequestBody AuthorizationRequest request) {
        AuthorizationResponse response = authorizationService.evaluate(
                request.identityToken(),
                request.action(),
                request.module(),
                request.resourceType()
        );
        return ResponseEntity.ok(response);
    }
}
