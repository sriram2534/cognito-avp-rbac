# AI agent guide

## Purpose

This is a Nexus authorization-source administration service. Cognito owns user
identity; MongoDB owns roles and permissions; an external authorization service
owns runtime allow/deny decisions.

## Non-negotiable rules

1. Never use Cognito groups. User identity is Cognito `sub` only.
2. Never expose MongoDB `_id` in a response or use it as a public foreign key.
3. Use UUID `roleId` and `permissionId` for relationship records and API paths.
   `roleKey` is immutable display/integration data, not a foreign key.
4. Keep collections as `nexus_roles`, `nexus_user_roles`,
   `nexus_permissions`, and `nexus_role_permissions`.
5. Preserve unique indexes on `(userSub, roleId)` and `(roleId, permissionId)`.
   Remove relationships through status changes, not hard deletion.
6. Put `@AuthorizationAudit` on every captured mutation. The aspect derives
   field deltas and skips idempotent calls within the same Mongo transaction as
   the business mutation; services must not call `AuditService` directly.
7. Audit writes only. Never audit `GET` requests.
   Every audit record must contain authenticated `userSub` and `userEmail`.
8. Use MapStruct mappers in services rather than manually constructing entities
   or response DTOs.
9. Do not add event propagation unless it is explicitly requested.
10. Do not implement authorization decisions in this service. The gateway or
    external authorization service must protect management endpoints.
11. Before assigning users to a role, validate each distinct canonical Cognito
    `sub` with `CognitoUserDirectory`. Keep that remote validation outside the
    audited MongoDB transaction; `@ValidateCognitoUserSubs` provides the current
    boundary and normalizes the service argument.
12. Keep collection reads set-based. User pages use `roleKeysForUsers`; role
    permission listing uses `findByPermissionIdIn`. Do not reintroduce repository
    lookups inside mapping streams.
13. Maintain `RolePermission.validFrom` and `validUntil` through the domain
    methods: grant/restore opens a validity window and revoke closes it.

## Change checklist

For a new mutable authorization-source feature:

1. Update the document, repository index, DTO, and MapStruct mapper.
2. Add precise validation and idempotent status semantics.
3. Choose an `AuthorizationAuditOperation` and annotate the public mutation
   when its resulting action is included by `AuditActionFilter`.
4. Update [nexus-role-model.md](nexus-role-model.md).
5. Run:

```bash
./mvnw test
git diff --check
```

## Key locations

| Area | Location |
|---|---|
| Nexus roles and user roles | `role/` |
| Permissions and assignments | `permission/` |
| Role and membership workflow | `service/RoleService.java` |
| Role-permission workflow | `service/RolePermissionService.java` |
| Cognito assignment validation | `service/CognitoUserDirectory.java`, `service/CognitoUserValidationAspect.java` |
| Declarative audit | `audit/AuthorizationAudit.java`, `audit/AuthorizationAuditAspect.java` |
| API | `controller/RoleController.java`, `controller/PermissionController.java` |
