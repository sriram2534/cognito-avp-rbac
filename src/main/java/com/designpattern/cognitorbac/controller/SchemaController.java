package com.designpattern.cognitorbac.controller;

import com.designpattern.cognitorbac.avp.SecurityContextHelper;
import com.designpattern.cognitorbac.config.VerifiedPermissionsProperties;
import com.designpattern.cognitorbac.dto.avp.AddActionRequest;
import com.designpattern.cognitorbac.dto.avp.SchemaResponse;
import com.designpattern.cognitorbac.service.PolicyStoreBootstrapService;
import com.designpattern.cognitorbac.service.SchemaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST API for managing the Cedar schema and bootstrapping the AVP Policy Store.
 *
 * <p>All schema operations are restricted to super admins only, since schema changes
 * affect the entire system (all modules).</p>
 */
@RestController
@RequestMapping("/api/v1/schema")
@PreAuthorize("isAuthenticated()")
public class SchemaController {

    private final SchemaService schemaService;
    private final PolicyStoreBootstrapService bootstrapService;
    private final SecurityContextHelper securityContextHelper;
    private final VerifiedPermissionsProperties avpProperties;

    public SchemaController(SchemaService schemaService,
                            PolicyStoreBootstrapService bootstrapService,
                            SecurityContextHelper securityContextHelper,
                            VerifiedPermissionsProperties avpProperties) {
        this.schemaService = schemaService;
        this.bootstrapService = bootstrapService;
        this.securityContextHelper = securityContextHelper;
        this.avpProperties = avpProperties;
    }

    /**
     * Gets the current Cedar schema summary (actions, entity types, metadata).
     */
    @GetMapping
    public ResponseEntity<SchemaResponse> getSchema() {
        return ResponseEntity.ok(schemaService.getSchema());
    }

    /**
     * Gets the raw Cedar schema JSON currently stored in AVP.
     */
    @GetMapping("/raw")
    public ResponseEntity<String> getRawSchema() {
        requireSuperAdmin();
        return ResponseEntity.ok(schemaService.getCurrentSchemaJson());
    }

    /**
     * Uploads the base Cedar schema from the classpath template to AVP.
     * This replaces the entire schema — use with caution.
     * Only super admins can perform this operation.
     */
    @PutMapping
    public ResponseEntity<SchemaResponse> putBaseSchema() {
        requireSuperAdmin();
        SchemaResponse response = schemaService.putBaseSchema();
        return ResponseEntity.ok(response);
    }

    /**
     * Adds a new action to the schema. Requires super admin access.
     * The action is added to the existing schema and the entire schema is re-uploaded.
     *
     * <p>Example: Adding a "Download" action that inherits from "Read":
     * <pre>
     * {
     *   "actionName": "Download",
     *   "memberOf": ["Read"],
     *   "description": "Allows downloading exported files"
     * }
     * </pre>
     */
    @PostMapping("/actions")
    public ResponseEntity<SchemaResponse> addAction(@Valid @RequestBody AddActionRequest request) {
        requireSuperAdmin();
        SchemaResponse response = schemaService.addAction(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Bootstraps the entire AVP setup: creates policy store, uploads schema,
     * links Cognito identity source, and creates the super admin policy.
     *
     * <p><strong>WARNING:</strong> This is a one-time operation. Calling it again will
     * create a NEW policy store (the old one remains but is not used).</p>
     *
     * <p>Only super admins can bootstrap.</p>
     */
    @PostMapping("/bootstrap")
    public ResponseEntity<Map<String, String>> bootstrap() {
        requireSuperAdmin();
        String policyStoreId = bootstrapService.bootstrap();
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "policyStoreId", policyStoreId,
                "message", "Bootstrap complete. Update AVP_POLICY_STORE_ID env var with this value."
        ));
    }

    private void requireSuperAdmin() {
        List<String> groups = securityContextHelper.getCallerGroups();
        if (!groups.contains(avpProperties.getSuperAdminGroup())) {
            throw new AccessDeniedException("Only super admins can modify the schema");
        }
    }
}
