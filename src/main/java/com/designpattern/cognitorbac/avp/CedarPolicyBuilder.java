package com.designpattern.cognitorbac.avp;

/**
 * Utility to generate Cedar policy statements programmatically.
 * Ensures consistent formatting and prevents injection of invalid Cedar syntax.
 */
public final class CedarPolicyBuilder {

    private CedarPolicyBuilder() {
    }

    /**
     * Builds a permit policy for a super admin group (unrestricted access).
     */
    public static String superAdminPolicy(String namespace, String groupName) {
        return """
                permit (
                    principal in %s::Group::"%s",
                    action,
                    resource
                );""".formatted(namespace, groupName);
    }

    /**
     * Builds a permit policy for a module admin group (all actions within a module).
     * The group's resource field is "global", meaning access to all resources in the module.
     */
    public static String moduleAdminPolicy(String namespace, String groupName, String module) {
        return """
                permit (
                    principal in %s::Group::"%s",
                    action,
                    resource
                ) when {
                    resource.module == "%s"
                };""".formatted(namespace, groupName, module);
    }

    /**
     * Builds a forbid policy that prevents a module admin from accessing other modules.
     */
    public static String moduleAdminForbidPolicy(String namespace, String groupName, String module) {
        return """
                forbid (
                    principal in %s::Group::"%s",
                    action,
                    resource
                ) unless {
                    resource.module == "%s"
                };""".formatted(namespace, groupName, module);
    }

    /**
     * Builds a permit policy for a resource-level group (scoped to module + resourceType).
     *
     * @param namespace    the Cedar namespace (e.g. "Portal")
     * @param groupName    the Cognito group (e.g. "ops:store:write")
     * @param action       the Cedar action (e.g. "Write", "Read", "Publish")
     * @param module       the resource module (e.g. "ops")
     * @param resourceType the resource type (e.g. "store")
     */
    public static String resourcePolicy(String namespace, String groupName,
                                         String action, String module, String resourceType) {
        return """
                permit (
                    principal in %s::Group::"%s",
                    action in [%s::Action::"%s"],
                    resource
                ) when {
                    resource.module == "%s" && resource.resourceType == "%s"
                };""".formatted(namespace, groupName, namespace, action, module, resourceType);
    }

    /**
     * Builds the global creation-guard forbid policy that prevents anyone except
     * the super admin from creating/managing a module-admin group. The decision
     * keys off the {@code resourceType == "moduleAdminGroup"} attribute supplied
     * by the application at authorization time.
     *
     * @param namespace       the Cedar namespace (e.g. "Portal")
     * @param superAdminGroup the super admin group name (e.g. "global:global:admin")
     */
    public static String moduleAdminCreationGuardPolicy(String namespace, String superAdminGroup) {
        return """
                forbid (
                    principal,
                    action,
                    resource
                ) when {
                    resource.resourceType == "moduleAdminGroup"
                } unless {
                    principal in %s::Group::"%s"
                };""".formatted(namespace, superAdminGroup);
    }

    /**
     * Builds a permit policy for a module-wide action (e.g. ops:global:write would mean
     * write on all resources in ops — though typically only admin uses global).
     */
    public static String moduleWidePolicy(String namespace, String groupName,
                                           String action, String module) {
        return """
                permit (
                    principal in %s::Group::"%s",
                    action in [%s::Action::"%s"],
                    resource
                ) when {
                    resource.module == "%s"
                };""".formatted(namespace, groupName, namespace, action, module);
    }
}
