# Nexus role data model and API

## Ownership boundary

| Concern | System of record |
|---|---|
| User identity and profile | Amazon Cognito |
| User identifier | Cognito `sub` |
| Roles | MongoDB `nexus_roles` |
| User-to-role membership | MongoDB `nexus_user_roles` |
| Permissions | MongoDB `nexus_permissions` |
| Role-to-permission assignment | MongoDB `nexus_role_permissions` |
| Audit and event delivery | MongoDB `audit_entries`, `authorization_outbox` |
| Runtime allow/deny decision | External authorization service |

This service never creates, reads, or mutates Cognito groups. It lists and
reads Cognito users from the user pool, then resolves their Nexus roles using
their Cognito `sub`.

## Collections

MongoDB `_id` is internal persistence metadata. It is never returned by API
responses or used as a public relationship key.

### `nexus_roles`

```json
{
  "roleId": "3a7c4f8e-f2a3-4cc7-8d83-9c5d28e56024",
  "roleKey": "deliveryops:developer",
  "module": "deliveryops",
  "name": "developer",
  "displayName": "DeliveryOps Developer",
  "description": "Create and update DeliveryOps stores",
  "status": "ACTIVE",
  "version": 0
}
```

- `roleId` is an immutable UUID and public API identifier: unique index.
- `roleKey` is immutable canonical lowercase `<module>:<role>`: unique index.
- `(module, name)` is unique; `(module, status)` supports catalog filtering.
- Roles are activated/deactivated; they are never hard-deleted.

### `nexus_user_roles`

```json
{
  "userSub": "cognito-sub",
  "roleId": "3a7c4f8e-f2a3-4cc7-8d83-9c5d28e56024",
  "status": "ACTIVE",
  "assignedAt": "2026-09-04T00:00:00Z",
  "removedAt": null,
  "version": 0
}
```

- `userSub` is Cognito `sub`, never username or email.
- `(userSub, roleId)` is unique. Removal changes status to `REMOVED`; assigning
  again restores the same document.
- `(userSub, status, roleId)` rebuilds one user’s role set efficiently.
- `(roleId, status, userSub)` supports role fan-out and membership listing.

### `nexus_permissions`

A permission has immutable lowercase coordinates:

```text
module:resourceType:access
deliveryops:stores:write
```

`permissionId` is an immutable UUID. Unique indexes exist on `permissionId`
and `(module, resourceType, access)`. Supported access values are `read`,
`write`, `export`, `approve`, and `manage`. A permission can be activated or
deactivated; its coordinates cannot change.

### `nexus_role_permissions`

```json
{
  "roleId": "3a7c4f8e-f2a3-4cc7-8d83-9c5d28e56024",
  "permissionId": "b9c211aa-5271-4a97-8ea8-4679cc4783dd",
  "status": "ACTIVE",
  "version": 0
}
```

- `(roleId, permissionId)` is unique.
- `(roleId, status, permissionId)` is the primary permission-set lookup.
- `(permissionId, status, roleId)` identifies roles affected by a permission
  change.
- Revocation retains history as `REVOKED`; a later grant restores it.
- An inactive role or permission cannot receive a new assignment.

## Fast authorization aggregation

The external authorization service should not aggregate Mongo collections on
every request. Keep two cache layers:

```text
userSub -> active roleIds
roleId  -> active permissionIds
permissionId -> immutable permission coordinates/status
```

For a decision, read active role IDs, inspect each role’s permission set, and
short-circuit when the requested permission is found. A user normally has few
roles, so this avoids expensive per-user permission fanout.

Mongo aggregation is a cache-miss or rebuild operation only:

```text
nexus_user_roles (userSub, ACTIVE)
  -> nexus_roles (ACTIVE)
  -> nexus_role_permissions (ACTIVE)
  -> nexus_permissions (ACTIVE)
  -> distinct permissionId
```

The compound indexes above support each join direction. A high-QPS
authorization service may also cache `userSub -> effective permission set`, but
that is a derived optimization rather than source data.

## HTTP API

All API routes require a valid Cognito JWT. External authorization must grant
or deny administrative access before requests reach this service. Every write
requires `X-Audit-Reason`; `X-Correlation-Id` is optional and is returned as
`X-Request-Id`.

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/users` | List Cognito users; `includeRoles=true` includes Nexus role keys. |
| `GET` | `/api/v1/users/{username}` | Get Cognito identity plus active Nexus role keys. |
| `GET` | `/api/v1/roles` | List Nexus roles. |
| `POST` | `/api/v1/roles` | Create role. |
| `GET` | `/api/v1/roles/{roleId}` | Get role. |
| `PATCH` | `/api/v1/roles/{roleId}` | Change display name or description. |
| `POST` | `/api/v1/roles/{roleId}/activate` | Activate role. |
| `POST` | `/api/v1/roles/{roleId}/deactivate` | Deactivate role. |
| `GET` | `/api/v1/roles/{roleId}/users` | List active user-role relationships. |
| `POST` | `/api/v1/roles/{roleId}/users` | Add or restore user-role relationships. |
| `DELETE` | `/api/v1/roles/{roleId}/users/{userSub}` | Remove user from role. |
| `GET` | `/api/v1/roles/{roleId}/permissions` | List role permissions. |
| `POST` | `/api/v1/roles/{roleId}/permissions/{permissionId}` | Grant permission. |
| `DELETE` | `/api/v1/roles/{roleId}/permissions/{permissionId}` | Revoke permission. |
| `GET` | `/api/v1/permissions` | List permissions. |
| `POST` | `/api/v1/permissions` | Create permission. |
| `GET` | `/api/v1/permissions/{permissionId}` | Get permission. |
| `PATCH` | `/api/v1/permissions/{permissionId}` | Change description. |
| `POST` | `/api/v1/permissions/{permissionId}/activate` | Activate permission. |
| `POST` | `/api/v1/permissions/{permissionId}/deactivate` | Deactivate permission. |
| `GET` | `/api/v1/permissions/{permissionId}/roles` | List active role-permission assignments. |
| `GET` | `/api/v1/audit/**` | Search audit history. |

Create a role:

```json
POST /api/v1/roles
{
  "roleKey": "deliveryops:developer",
  "displayName": "DeliveryOps Developer",
  "description": "Create and update DeliveryOps stores"
}
```

Assign Cognito users by immutable subject:

```json
POST /api/v1/roles/{roleId}/users
{
  "userSubs": ["1f667d9c-2048-4e08-b221-7929924b7a44"]
}
```

## Audit and outbox events

All role, user-role, permission, and role-permission source changes persist
their source document, audit record, and outbox record in one MongoDB
transaction. MongoDB must run as a replica set or sharded cluster. Reads are
not audited.

| Event | Trigger | Consumer action |
|---|---|---|
| `ROLE_CHANGED` | Role create/update/status | Invalidate role metadata. |
| `USER_ROLE_MEMBERSHIP_CHANGED` | User assigned, restored, or removed | Invalidate the affected user’s role/effective-permission cache. |
| `ROLE_PERMISSIONS_CHANGED` | Permission grant/revoke/restore | Invalidate role permission cache. |
| `PERMISSION_CHANGED` | Permission description/status | Invalidate the permission and affected roles. |
| `AUDIT_RECORDED` | Role and user-role mutation | Send durable audit activity to audit consumers. |

Every event contains `eventVersion`, `eventType`, `correlationId`, and
`occurredAt`. SNS publishing is asynchronous and at-least-once; consumers must
de-duplicate messages by `eventId`.

## Migration

This implementation writes only `nexus_*` collections. It intentionally does
not auto-migrate prior collections because mapping legacy Cognito group names to
new database `roleId` values is a business-data migration that needs review.

1. Export old permissions and create matching `nexus_permissions` documents.
2. Create `nexus_roles` documents with the intended two-part role key.
3. Translate old membership to `nexus_user_roles` using each Cognito `sub`.
4. Translate assignments to `nexus_role_permissions` using new `roleId` and
   corresponding `permissionId`.
5. Rebuild authorization-service caches and verify allow/deny behavior.
6. Retire old `/api/v1/groups` clients and collections only after verification.
