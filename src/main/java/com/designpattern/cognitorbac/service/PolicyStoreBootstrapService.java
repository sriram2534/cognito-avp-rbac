package com.designpattern.cognitorbac.service;

import com.designpattern.cognitorbac.config.CognitoProperties;
import com.designpattern.cognitorbac.config.VerifiedPermissionsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.verifiedpermissions.VerifiedPermissionsClient;
import software.amazon.awssdk.services.verifiedpermissions.model.CognitoGroupConfiguration;
import software.amazon.awssdk.services.verifiedpermissions.model.CognitoUserPoolConfiguration;
import software.amazon.awssdk.services.verifiedpermissions.model.Configuration;
import software.amazon.awssdk.services.verifiedpermissions.model.CreateIdentitySourceRequest;
import software.amazon.awssdk.services.verifiedpermissions.model.CreateIdentitySourceResponse;
import software.amazon.awssdk.services.verifiedpermissions.model.CreatePolicyStoreRequest;
import software.amazon.awssdk.services.verifiedpermissions.model.CreatePolicyStoreResponse;
import software.amazon.awssdk.services.verifiedpermissions.model.ValidationSettings;
import software.amazon.awssdk.services.verifiedpermissions.model.ValidationMode;

import java.util.List;

/**
 * One-time bootstrap operations for setting up the AVP Policy Store.
 * Creates the policy store, uploads the schema, links the Cognito identity source,
 * and creates the baseline super admin policy.
 *
 * <p>This service should be invoked once during initial infrastructure setup,
 * typically by a super admin or as part of a deployment pipeline.</p>
 */
@Service
public class PolicyStoreBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(PolicyStoreBootstrapService.class);

    private final VerifiedPermissionsClient avpClient;
    private final VerifiedPermissionsProperties avpProperties;
    private final CognitoProperties cognitoProperties;
    private final SchemaService schemaService;
    private final PolicyService policyService;

    public PolicyStoreBootstrapService(VerifiedPermissionsClient avpClient,
                                       VerifiedPermissionsProperties avpProperties,
                                       CognitoProperties cognitoProperties,
                                       SchemaService schemaService,
                                       PolicyService policyService) {
        this.avpClient = avpClient;
        this.avpProperties = avpProperties;
        this.cognitoProperties = cognitoProperties;
        this.schemaService = schemaService;
        this.policyService = policyService;
    }

    /**
     * Full bootstrap: creates policy store, uploads schema, creates identity source,
     * and creates the super admin policy.
     *
     * @return the newly created policy store ID
     */
    public String bootstrap() {
        // Step 1: Create Policy Store
        String policyStoreId = createPolicyStore();
        avpProperties.setPolicyStoreId(policyStoreId);

        // Step 2: Upload base Cedar schema
        schemaService.putBaseSchema();
        log.info("Base schema uploaded to policy store [{}]", policyStoreId);

        // Step 3: Create Identity Source (link Cognito)
        String identitySourceId = createIdentitySource(policyStoreId);
        log.info("Identity source [{}] created for policy store [{}]", identitySourceId, policyStoreId);

        // Step 4: Create super admin policy
        createSuperAdminPolicy();
        log.info("Super admin policy created");

        // Step 5: Create the module-admin creation guard (only super admin may create module admins)
        createModuleAdminGuardPolicy();
        log.info("Module-admin creation guard policy created");

        log.info("Bootstrap complete. Policy Store ID: {}", policyStoreId);
        return policyStoreId;
    }

    /**
     * Creates an AVP Policy Store with STRICT validation mode.
     */
    public String createPolicyStore() {
        CreatePolicyStoreResponse response = avpClient.createPolicyStore(
                CreatePolicyStoreRequest.builder()
                        .validationSettings(ValidationSettings.builder()
                                .mode(ValidationMode.STRICT)
                                .build())
                        .build());

        String policyStoreId = response.policyStoreId();
        log.info("Created Policy Store: {}", policyStoreId);
        return policyStoreId;
    }

    /**
     * Creates the identity source linking the Cognito User Pool to the policy store.
     */
    public String createIdentitySource(String policyStoreId) {
        String namespace = avpProperties.getNamespace();
        String userPoolArn = avpProperties.getUserPoolArn();

        if (userPoolArn == null || userPoolArn.isBlank()) {
            throw new IllegalStateException(
                    "COGNITO_USER_POOL_ARN must be set to create an identity source");
        }

        CreateIdentitySourceResponse response = avpClient.createIdentitySource(
                CreateIdentitySourceRequest.builder()
                        .policyStoreId(policyStoreId)
                        .principalEntityType(namespace + "::User")
                        .configuration(Configuration.builder()
                                .cognitoUserPoolConfiguration(CognitoUserPoolConfiguration.builder()
                                        .userPoolArn(userPoolArn)
                                        .clientIds(List.of(cognitoProperties.getAppClientId()))
                                        .groupConfiguration(CognitoGroupConfiguration.builder()
                                                .groupEntityType(namespace + "::Group")
                                                .build())
                                        .build())
                                .build())
                        .build());

        return response.identitySourceId();
    }

    /**
     * Creates the baseline super admin policy that grants unrestricted access.
     */
    private void createSuperAdminPolicy() {
        String superAdminGroup = avpProperties.getSuperAdminGroup();
        List<String> bootstrapGroups = List.of(superAdminGroup);

        com.designpattern.cognitorbac.dto.avp.CreatePolicyRequest request =
                new com.designpattern.cognitorbac.dto.avp.CreatePolicyRequest(
                        superAdminGroup,
                        "Admin",
                        "global",
                        "global",
                        "Super admin: unrestricted access to all modules and resources"
                );

        policyService.createPolicy(request, bootstrapGroups);
    }

    /**
     * Creates the global guard policy that forbids anyone except the super admin
     * from creating/managing a module-admin group (resourceType == "moduleAdminGroup").
     */
    private void createModuleAdminGuardPolicy() {
        String namespace = avpProperties.getNamespace();
        String superAdminGroup = avpProperties.getSuperAdminGroup();
        String statement = com.designpattern.cognitorbac.avp.CedarPolicyBuilder
                .moduleAdminCreationGuardPolicy(namespace, superAdminGroup);
        policyService.createStaticPolicy(statement,
                "Creation guard: only super admin may create module-admin groups");
    }
}
