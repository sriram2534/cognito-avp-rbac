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
6. Keep role, membership, permission, and role-permission changes
   transactional with audit and outbox writes.
7. Audit writes only. Never audit `GET` requests.
8. Use MapStruct mappers in services rather than manually constructing entities
   or response DTOs.
9. Emit versioned outbox events. Consumers handle at-least-once delivery and
   deduplicate with `eventId`.
10. Do not implement authorization decisions in this service. The gateway or
    external authorization service must protect management endpoints.

## Change checklist

For a new mutable authorization-source feature:

1. Update the document, repository index, DTO, and MapStruct mapper.
2. Add precise validation and idempotent status semantics.
3. Update the service transaction to persist field-level audit history and an
   invalidation event.
4. Keep event contracts backward compatible; add an `eventVersion` for changes.
5. Update [nexus-role-model.md](nexus-role-model.md).
6. Run:

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
| Audit | `audit/` |
| SNS transactional outbox | `outbox/` |
| API | `controller/RoleController.java`, `controller/PermissionController.java` |
