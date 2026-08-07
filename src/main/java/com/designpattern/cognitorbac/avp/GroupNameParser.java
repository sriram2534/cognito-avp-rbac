package com.designpattern.cognitorbac.avp;

/**
 * Parses a Cognito group name in the format {@code module:resource:access}
 * into its constituent parts.
 */
public record GroupNameParser(String module, String resource, String access) {

    private static final String GLOBAL_RESOURCE = "global";

    /**
     * Parses a group name string into its parts.
     *
     * @throws IllegalArgumentException if the group name doesn't match the expected format
     */
    public static GroupNameParser parse(String groupName) {
        if (groupName == null || groupName.isBlank()) {
            throw new IllegalArgumentException("groupName must not be blank");
        }
        String[] parts = groupName.split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException(
                    "groupName must follow pattern module:resource:access, got: " + groupName);
        }
        return new GroupNameParser(parts[0].trim(), parts[1].trim(), parts[2].trim());
    }

    /**
     * Returns true if this is a module-level admin group (resource == "global").
     */
    public boolean isModuleAdmin() {
        return GLOBAL_RESOURCE.equals(resource) && "admin".equals(access);
    }

    /**
     * Returns true if this is the super admin group (module == "global" AND resource == "global").
     */
    public boolean isSuperAdmin() {
        return "global".equals(module) && GLOBAL_RESOURCE.equals(resource) && "admin".equals(access);
    }

    /**
     * Returns true if this is a resource-level group (resource != "global").
     */
    public boolean isResourceLevel() {
        return !GLOBAL_RESOURCE.equals(resource);
    }

    /**
     * Returns the Cedar action name from the access level.
     * Capitalizes the first letter (e.g. "write" → "Write").
     */
    public String cedarAction() {
        if (access == null || access.isBlank()) {
            throw new IllegalArgumentException("access level must not be blank");
        }
        return access.substring(0, 1).toUpperCase() + access.substring(1).toLowerCase();
    }
}
