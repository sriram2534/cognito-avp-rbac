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
| Audit history | MongoDB `audit_entries` |
| Pending integration events | MongoDB `outbox_events` |
| Runtime allow/deny decision | Future external authorization service |

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
- `(module, name)` is unique; catalog indexes beginning with `status` or
  `module` support filtered, deterministic catalog pagination.
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
and `(module, resourceType, access)`. A catalog index on status and the
permission coordinates supports filtered, deterministic pagination. Supported
access values are `read`, `write`, `export`, `approve`, and `manage`. A
permission can be activated or deactivated; its coordinates cannot change.

### `nexus_role_permissions`

```json
{
  "roleId": "3a7c4f8e-f2a3-4cc7-8d83-9c5d28e56024",
  "permissionId": "b9c211aa-5271-4a97-8ea8-4679cc4783dd",
  "status": "ACTIVE",
  "validFrom": "2026-09-04T00:00:00Z",
  "validUntil": null,
  "version": 0
}
```

- `(roleId, permissionId)` is unique.
- `(roleId, status, permissionId)` is the primary permission-set lookup.
- `(permissionId, status, roleId)` identifies roles affected by a permission
  change.
- Revocation retains history as `REVOKED`; a later grant restores it.
- `validFrom` records the current grant/restore time. Revocation sets
  `validUntil`; restoring starts a new validity window and clears `validUntil`.
- An inactive role or permission cannot receive a new assignment.

## Fast authorization aggregation

The external authorization service is intentionally not part of this project
and will be implemented later. Its planned design should not aggregate Mongo
collections on every request. It should keep two cache layers:

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

Administration reads avoid per-document lookups. A Cognito user page resolves
all memberships with one `userSub in (...)` query and all referenced roles with
one `roleId in (...)` query. Listing a role's permissions similarly loads its
relationships once and its permission documents with one `permissionId in
(...)` query.

## HTTP API

All API routes require a Cognito access token containing `token_use=access`,
`sub`, `email`, and the configured application-client audience/client ID.
The future external authorization service must grant or deny administrative
access before requests reach this service. Until then, private ingress must
prevent end-user access. Every write requires a nonblank `X-Audit-Reason`;
`X-Correlation-Id` is optional and is returned as `X-Request-Id`.

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/users` | List Cognito users; `includeRoles=true` includes Nexus role keys. |
| `GET` | `/api/v1/users/{username}` | Get Cognito identity plus active Nexus role keys. |
| `GET` | `/api/v1/roles` | Paginate and filter Nexus roles. |
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
| `GET` | `/api/v1/permissions` | Paginate and filter permissions. |
| `POST` | `/api/v1/permissions` | Create permission. |
| `GET` | `/api/v1/permissions/{permissionId}` | Get permission. |
| `PATCH` | `/api/v1/permissions/{permissionId}` | Change description. |
| `POST` | `/api/v1/permissions/{permissionId}/activate` | Activate permission. |
| `POST` | `/api/v1/permissions/{permissionId}/deactivate` | Deactivate permission. |
| `GET` | `/api/v1/permissions/{permissionId}/roles` | List active role-permission assignments. |
| `GET` | `/api/v1/audit/**` | Search audit history. |

### Role and permission catalog pagination

Both catalog endpoints use zero-based offset pagination. The default page size
is 20 and the maximum is 100. Invalid page, size, direction, status, or sort
values return `400 VALIDATION_FAILED`.

Role query parameters:

| Parameter | Meaning |
|---|---|
| `page`, `size` | Zero-based page number and page size. |
| `module` | Exact case-normalized module filter. |
| `status` | Exact `ACTIVE` or `INACTIVE` filter. |
| `search` | Case-insensitive literal substring across role key, name, display name, and description. |
| `sortBy` | `roleKey`, `module`, `name`, `displayName`, `status`, `createdAt`, or `updatedAt`. Default: `module`. |
| `direction` | `asc` or `desc`. Default: `asc`. |

Permission query parameters:

| Parameter | Meaning |
|---|---|
| `page`, `size` | Zero-based page number and page size. |
| `module`, `resourceType`, `access` | Exact case-normalized coordinate filters. |
| `status` | Exact `ACTIVE` or `INACTIVE` filter. |
| `search` | Case-insensitive literal substring across module, resource type, access, and description. |
| `sortBy` | `displayKey`, `module`, `resourceType`, `access`, `status`, `createdAt`, or `updatedAt`. Default: `displayKey`. |
| `direction` | `asc` or `desc`. Default: `asc`. |

Example:

```text
GET /api/v1/permissions?module=deliveryops&status=ACTIVE&page=0&size=25&sortBy=displayKey&direction=asc
```

```json
{
  "items": [
    {
      "permissionId": "b9c211aa-5271-4a97-8ea8-4679cc4783dd",
      "module": "deliveryops",
      "resourceType": "stores",
      "access": "write",
      "displayKey": "deliveryops:stores:write",
      "status": "ACTIVE"
    }
  ],
  "page": 0,
  "size": 25,
  "totalElements": 1,
  "totalPages": 1,
  "first": true,
  "last": true,
  "hasNext": false,
  "hasPrevious": false
}
```

Every exposed sort expands to a deterministic MongoDB sort ending in the
external UUID, preventing items from moving between adjacent pages when the
primary sort value is duplicated. Exact coordinate and status filters use the
catalog indexes. `search` safely escapes regular-expression characters, but a
broad substring search can scan the catalog; prefer exact filters for large
datasets or introduce MongoDB Atlas Search as a separately reviewed feature.

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

The request accepts 1–50 subjects. Subjects must be canonical UUIDs. Before any
database mutation, the service resolves each distinct subject with an exact
Cognito `ListUsers` filter, then confirms the returned username with
`AdminGetUser`. Validation intentionally runs outside the audited MongoDB
transaction so AWS calls cannot hold database transaction resources. The
request is all-or-nothing: if any subject is malformed, unknown, inconsistent,
or cannot be verified because Cognito is unavailable, no membership or audit
record is written. `ListUsers` is eventually consistent, so a user created
immediately before assignment can briefly receive `404`; clients may retry that
case after a short delay.

## Audit capture

Mutating service methods declare their audit intent with `@AuthorizationAudit`.
`AuthorizationAuditAspect` opens the outer MongoDB transaction, snapshots the
required before-state, executes the mutation, persists the audit document, and
enqueues an authorization-change outbox event when the change can affect an
existing session. The mutation, audit document, and applicable outbox document
commit together. Failure of either companion write rolls the mutation back,
while idempotent requests create neither audit nor outbox records. Reads are not
audited. A background dispatcher later sends committed outbox events to the
auth-service SQS queue without extending request latency.
Bulk user assignment snapshots existing relationships with one set-based query,
then writes new/restored relationships in a batch before their audit and outbox
records are committed.

Every new audit document contains the authenticated caller's mandatory
`userSub` and `userEmail`. Role-related events contain both `roleName` (for
example, `developer`) and immutable `roleKey` (for example,
`deliveryops:developer`). For user-role changes, `changes.targetUserSub`
identifies the affected user separately from the caller.

The audit filter captures only:

- role created, activated, and deactivated;
- user assigned, removed, or restored in a role;
- permission created, activated, and deactivated; and
- role-permission granted, revoked, or restored.

Role metadata changes and permission-description changes are intentionally not
captured by this filter.

The same captured session-affecting changes publish invalidation events. Role
or permission creation alone has no affected session and does not publish. See
[transactional-outbox.md](transactional-outbox.md) for the event matrix,
delivery guarantees, configuration, and consumer contract.

Existing audit documents using `group_name`, `actor_sub`, or `actor_email`
require the one-time migration described in
[audit-schema-migration.md](audit-schema-migration.md).

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
