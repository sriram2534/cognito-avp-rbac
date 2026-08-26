# Cognito roles and MongoDB permissions

## Ownership

- Cognito owns users, coarse role groups, and user-to-role membership.
- MongoDB owns reusable permissions, role_permissions, audit entries, and
  authorization invalidation outbox records.
- AWS Verified Permissions policies are static infrastructure. This service does
  not create, update, or delete a policy as part of group, permission, or
  role-permission lifecycle work.

The Cognito group name is the role identifier. It must use canonical lowercase
<module>:<role> form, for example deliveryops:developer. Permission-shaped
groups such as deliveryops:stores:write are rejected.

## Data model

Each permissions document has a MongoDB _id and a separate immutable UUID
permissionId. The UUID is the public and relationship identifier; MongoDB _id
is never used as a foreign key. Permissions has unique indexes on permissionId
and on (module, resourceType, access). Coordinates are lower-case and immutable;
only description and lifecycle state can change.

role_permissions has one row per Cognito role and permission ID, with a unique
index on (roleKey, permissionId), where permissionId is the permission UUID.
Revocation is retained as REVOKED history; a subsequent grant restores the same
relationship.

## APIs

- POST /api/v1/permissions
- GET /api/v1/permissions
- GET /api/v1/permissions/{permissionId}
- PATCH /api/v1/permissions/{permissionId} (description only)
- POST /api/v1/permissions/{permissionId}/deactivate
- POST /api/v1/permissions/{permissionId}/activate
- GET /api/v1/permissions/{permissionId}/groups
- POST /api/v1/groups/{roleKey}/permissions/{permissionId}
- DELETE /api/v1/groups/{roleKey}/permissions/{permissionId}
- GET /api/v1/groups/{roleKey}/permissions

All writes require X-Audit-Reason and the configured Cognito administrative
role. The append-only audit record includes actor, correlation ID, aggregate
reference, and field-level before/after values. Permission and role-permission
writes use a MongoDB transaction together with their audit and outbox records.
MongoDB must therefore run as a replica set (or sharded cluster) in deployed
environments.

## Invalidation events

Committed source changes are saved into authorization_outbox. When
AUTHORIZATION_EVENTS_TOPIC_ARN is configured, the outbox publisher delivers
them to SNS. Delivery is at-least-once and includes eventId for consumer
deduplication.

- ROLE_PERMISSIONS_CHANGED invalidates the affected role-permission cache in
  nexus-auth-svc.
- PERMISSION_CHANGED supplies all affected active role keys.
- USER_GROUP_MEMBERSHIP_CHANGED supplies Cognito sub and the changed role.

## Migration safety

The legacy AVP policy endpoints and existing per-group AVP policies remain
available during cutover, but GroupService no longer invokes them. Do not delete
legacy AVP policies until nexus-auth-svc has deployed the structured
IsAuthorized flow, reusable policy schema, effective-permission cache, and
end-to-end allow/deny verification.
