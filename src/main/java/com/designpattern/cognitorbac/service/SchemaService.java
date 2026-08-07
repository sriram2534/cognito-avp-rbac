package com.designpattern.cognitorbac.service;

import com.designpattern.cognitorbac.config.VerifiedPermissionsProperties;
import com.designpattern.cognitorbac.dto.avp.AddActionRequest;
import com.designpattern.cognitorbac.dto.avp.SchemaResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.verifiedpermissions.VerifiedPermissionsClient;
import software.amazon.awssdk.services.verifiedpermissions.model.GetSchemaRequest;
import software.amazon.awssdk.services.verifiedpermissions.model.GetSchemaResponse;
import software.amazon.awssdk.services.verifiedpermissions.model.PutSchemaRequest;
import software.amazon.awssdk.services.verifiedpermissions.model.PutSchemaResponse;
import software.amazon.awssdk.services.verifiedpermissions.model.SchemaDefinition;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages the Cedar schema in AVP. Supports reading the current schema,
 * uploading the base schema, and dynamically adding new actions.
 */
@Service
public class SchemaService {

    private static final Logger log = LoggerFactory.getLogger(SchemaService.class);
    private static final String SCHEMA_RESOURCE_PATH = "cedar/schema.json";

    private final VerifiedPermissionsClient avpClient;
    private final VerifiedPermissionsProperties properties;
    private final ObjectMapper objectMapper;

    public SchemaService(VerifiedPermissionsClient avpClient,
                         VerifiedPermissionsProperties properties,
                         ObjectMapper objectMapper) {
        this.avpClient = avpClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Retrieves the current schema from AVP and returns a summary response.
     */
    public SchemaResponse getSchema() {
        GetSchemaResponse response = avpClient.getSchema(GetSchemaRequest.builder()
                .policyStoreId(properties.getPolicyStoreId())
                .build());

        String schemaJson = response.schema();
        List<String> actions = extractActions(schemaJson);
        List<String> entityTypes = extractEntityTypes(schemaJson);

        return new SchemaResponse(
                properties.getPolicyStoreId(),
                properties.getNamespace(),
                actions,
                entityTypes,
                response.createdDate()
        );
    }

    /**
     * Uploads the base Cedar schema from the classpath resource to AVP.
     * This is idempotent — it replaces the existing schema entirely.
     */
    public SchemaResponse putBaseSchema() {
        String schemaJson = loadBaseSchema();
        return putSchema(schemaJson);
    }

    /**
     * Adds a new action to the existing schema and uploads it to AVP.
     * The action is added with the specified parent actions (memberOf).
     *
     * @throws IllegalArgumentException if the action already exists
     */
    public SchemaResponse addAction(AddActionRequest request) {
        String currentSchema = getCurrentSchemaJson();
        String updatedSchema = addActionToSchema(currentSchema, request);
        return putSchema(updatedSchema);
    }

    /**
     * Returns the raw Cedar schema JSON currently stored in AVP.
     */
    public String getCurrentSchemaJson() {
        GetSchemaResponse response = avpClient.getSchema(GetSchemaRequest.builder()
                .policyStoreId(properties.getPolicyStoreId())
                .build());
        return response.schema();
    }

    private SchemaResponse putSchema(String schemaJson) {
        PutSchemaResponse response = avpClient.putSchema(PutSchemaRequest.builder()
                .policyStoreId(properties.getPolicyStoreId())
                .definition(SchemaDefinition.builder()
                        .cedarJson(schemaJson)
                        .build())
                .build());

        log.info("Schema uploaded to policy store: {}", properties.getPolicyStoreId());

        List<String> actions = extractActions(schemaJson);
        List<String> entityTypes = extractEntityTypes(schemaJson);

        return new SchemaResponse(
                properties.getPolicyStoreId(),
                properties.getNamespace(),
                actions,
                entityTypes,
                response.createdDate()
        );
    }

    private String addActionToSchema(String schemaJson, AddActionRequest request) {
        try {
            Map<String, Object> schema = objectMapper.readValue(schemaJson, new TypeReference<>() {});
            String namespace = properties.getNamespace();

            @SuppressWarnings("unchecked")
            Map<String, Object> nsMap = (Map<String, Object>) schema.get(namespace);
            if (nsMap == null) {
                throw new IllegalStateException("Namespace '" + namespace + "' not found in schema");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> actions = (Map<String, Object>) nsMap.get("actions");
            if (actions == null) {
                actions = new LinkedHashMap<>();
                nsMap.put("actions", actions);
            }

            if (actions.containsKey(request.actionName())) {
                throw new IllegalArgumentException("Action already exists: " + request.actionName());
            }

            // Build the new action definition
            Map<String, Object> newAction = new LinkedHashMap<>();

            // memberOf
            List<Map<String, String>> memberOfList = new ArrayList<>();
            for (String parent : request.memberOf()) {
                if (!actions.containsKey(parent)) {
                    throw new IllegalArgumentException("Parent action does not exist: " + parent);
                }
                memberOfList.add(Map.of("id", parent, "type", namespace + "::Action"));
            }
            newAction.put("memberOf", memberOfList);

            // appliesTo (same as other actions)
            Map<String, Object> appliesTo = new LinkedHashMap<>();
            appliesTo.put("principalTypes", List.of("User", "Group"));
            appliesTo.put("resourceTypes", List.of("Resource"));
            appliesTo.put("context", Map.of("type", "Record", "attributes", Map.of()));
            newAction.put("appliesTo", appliesTo);

            actions.put(request.actionName(), newAction);

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(schema);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse/modify schema JSON", e);
        }
    }

    private String loadBaseSchema() {
        try {
            ClassPathResource resource = new ClassPathResource(SCHEMA_RESOURCE_PATH);
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load base schema from classpath: " + SCHEMA_RESOURCE_PATH, e);
        }
    }

    private List<String> extractActions(String schemaJson) {
        try {
            Map<String, Object> schema = objectMapper.readValue(schemaJson, new TypeReference<>() {});
            @SuppressWarnings("unchecked")
            Map<String, Object> nsMap = (Map<String, Object>) schema.get(properties.getNamespace());
            if (nsMap == null) return List.of();
            @SuppressWarnings("unchecked")
            Map<String, Object> actions = (Map<String, Object>) nsMap.get("actions");
            return actions == null ? List.of() : new ArrayList<>(actions.keySet());
        } catch (JsonProcessingException e) {
            log.warn("Failed to extract actions from schema", e);
            return List.of();
        }
    }

    private List<String> extractEntityTypes(String schemaJson) {
        try {
            Map<String, Object> schema = objectMapper.readValue(schemaJson, new TypeReference<>() {});
            @SuppressWarnings("unchecked")
            Map<String, Object> nsMap = (Map<String, Object>) schema.get(properties.getNamespace());
            if (nsMap == null) return List.of();
            @SuppressWarnings("unchecked")
            Map<String, Object> entityTypes = (Map<String, Object>) nsMap.get("entityTypes");
            return entityTypes == null ? List.of() : new ArrayList<>(entityTypes.keySet());
        } catch (JsonProcessingException e) {
            log.warn("Failed to extract entity types from schema", e);
            return List.of();
        }
    }
}
