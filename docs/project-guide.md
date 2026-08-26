# Project guide

## What this service owns

`cognito-rbac` is an administrative API, not the runtime authorization engine.
It owns the source data that a runtime service (for example `nexus-auth-svc`)
uses to answer authorization requests.

| Concern | System of record | Notes |
|---|---|---|
| Users | Amazon Cognito | Users are listed from `cognito.source-group`. |
| Roles | Amazon Cognito groups | One canonical group name per role. |
| User-role membership | Amazon Cognito | Membership changes publish invalidation events. |
| Permissions | MongoDB `permissions` | Reusable and independently managed. |
| Role permissions | MongoDB `role_permissions` | The relationship from a Cognito role to a permission. |
| Audit history | MongoDB `audit_entries` | Append-only mutation history. |
| Event delivery | MongoDB `authorization_outbox`, optional SNS | Transactional outbox, at-least-once delivery. |
| Runtime authorization | Downstream authorization service | Makes allow/deny decisions from this service's source data and events. |

## Domain model and rules

### Roles

A role key is the Cognito group name and must be canonical lowercase
`<module>:<role>`. Each component is 1–63 characters and may contain lowercase
letters, digits, `_`, and `-`.

```text
Valid:   deliveryops:developer
Valid:   subscription:reviewer
Invalid: DeliveryOps:Developer     # not lowercase
Invalid: deliveryops:stores:write  # this is permission-shaped, not a role
```

### Permissions

A permission is an immutable triple:

```text
module:resourceType:access
deliveryops:stores:write
```

`module`, `resourceType`, and `access` are normalized to lowercase. Allowed
access values are `read`, `write`, `export`, `approve`, and `manage`. The triple
is immutable after creation; only its description and lifecycle status can
change.

Every permission has two IDs internally:

- MongoDB `_id`: persistence-only; never returned by the API and never used as
  a relationship key.
- `permissionId`: immutable UUID; returned by the API and stored in
  `role_permissions`.

### Role-permission assignment

The relationship is identified externally by `(roleKey, permissionId)`, not by
MongoDB `_id`. It has `ACTIVE` or `REVOKED` status. Revoking retains history;
granting the same revoked assignment restores it. An inactive permission cannot
be assigned to a role.

Deleting a role is blocked while it has active assignments. Revoke assignments
first, then delete the Cognito group.

## HTTP API

All endpoints except health require a Cognito JWT. Group, user, and audit reads
require an authenticated caller. The permission API and all role/permission
mutation routes require the configured admin group and an `X-Audit-Reason`
header.

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/actuator/health` | Unauthenticated health check. |
| `GET` | `/api/v1/users` | List users in the configured source group. |
| `GET` | `/api/v1/users/{username}` | Get one Cognito user. |
| `GET` | `/api/v1/groups` | List Cognito roles. |
| `POST` | `/api/v1/groups` | Create role. |
| `GET` | `/api/v1/groups/{roleKey}` | Get role. |
| `PUT` | `/api/v1/groups/{roleKey}` | Update role metadata. |
| `DELETE` | `/api/v1/groups/{roleKey}` | Delete role after assignments are revoked. |
| `GET` | `/api/v1/groups/{roleKey}/users` | List members. |
| `POST` | `/api/v1/groups/{roleKey}/users` | Add users to role. |
| `DELETE` | `/api/v1/groups/{roleKey}/users/{username}` | Remove member. |
| `GET` | `/api/v1/permissions` | List permissions. |
| `POST` | `/api/v1/permissions` | Create permission. |
| `GET` | `/api/v1/permissions/{permissionId}` | Get permission by UUID. |
| `PATCH` | `/api/v1/permissions/{permissionId}` | Change description only. |
| `POST` | `/api/v1/permissions/{permissionId}/activate` | Activate permission. |
| `POST` | `/api/v1/permissions/{permissionId}/deactivate` | Deactivate permission. |
| `GET` | `/api/v1/permissions/{permissionId}/groups` | List active role assignments. |
| `GET` | `/api/v1/groups/{roleKey}/permissions` | List the role's active permissions. |
| `POST` | `/api/v1/groups/{roleKey}/permissions/{permissionId}` | Grant permission. |
| `DELETE` | `/api/v1/groups/{roleKey}/permissions/{permissionId}` | Revoke permission. |
| `GET` | `/api/v1/audit/**` | Search read-only audit history. |

### Headers

```http
Authorization: Bearer <Cognito access token>  # required for all API routes
X-Audit-Reason: <human-readable reason>       # required for writes
X-Correlation-Id: <optional safe identifier>  # 1–128 [A-Za-z0-9._-]
```

The correlation ID is included in audit entries and outbox payloads. The
service returns the effective value in `X-Request-Id` for audited writes.

### Request and response examples

Create a permission:

```json
POST /api/v1/permissions
{
  "module": "deliveryops",
  "resourceType": "stores",
  "access": "write",
  "description": "Create and update stores"
}
```

```json
201 Created
{
  "permissionId": "6c90e52f-a5c6-4fbd-b682-bc3b350738ad",
  "module": "deliveryops",
  "resourceType": "stores",
  "access": "write",
  "displayKey": "deliveryops:stores:write",
  "description": "Create and update stores",
  "status": "ACTIVE",
  "createdAt": "2026-08-25T18:00:00Z",
  "updatedAt": "2026-08-25T18:00:00Z",
  "version": 0
}
```

Grant it to a role:

```json
POST /api/v1/groups/deliveryops:developer/permissions/6c90e52f-a5c6-4fbd-b682-bc3b350738ad
```

```json
201 Created
{
  "roleKey": "deliveryops:developer",
  "permissionId": "6c90e52f-a5c6-4fbd-b682-bc3b350738ad",
  "status": "ACTIVE",
  "validFrom": null,
  "validUntil": null,
  "createdAt": "2026-08-25T18:01:00Z",
  "updatedAt": "2026-08-25T18:01:00Z",
  "version": 0
}
```

MongoDB identifiers are deliberately absent from both response bodies.

## Audit and event flow

Writes are audited; reads are not. Permission and role-permission mutation
services record structured field changes and persist source data, audit data,
and outbox data within one MongoDB transaction.

```text
HTTP write
  -> authenticated/admin check
  -> audit context (actor, reason, correlation ID)
  -> source mutation in MongoDB
  -> audit entry + outbox event in the same transaction
  -> commit
  -> scheduled publisher sends pending outbox event to SNS (optional)
```

The publisher is intentionally at-least-once. Consumers must de-duplicate by
the `eventId` added at publish time.

| Event | Trigger | Key payload values |
|---|---|---|
| `PERMISSION_CHANGED` | Description or status changes | `permissionId`, `affectedRoleKeys` |
| `ROLE_PERMISSIONS_CHANGED` | Grant, revoke, or restore | `roleKey`, `rolePermissionVersion` |
| `USER_GROUP_MEMBERSHIP_CHANGED` | Add/remove user | `sub`, `roleKey`, `operation` |

Set `AUTHORIZATION_EVENTS_TOPIC_ARN` to enable SNS delivery. With no topic ARN,
outbox records remain pending; this is useful only when a separate delivery
mechanism is intended.

## Configuration and deployment

Configuration is in `src/main/resources/application.yaml`; values should come
from environment variables or deployment configuration, never committed
credentials.

| Variable | Required | Purpose |
|---|---:|---|
| `COGNITO_REGION` | Yes | Cognito region; defaults to `us-east-1`. |
| `COGNITO_USER_POOL_ID` | Yes | User pool used for users, roles, and JWT issuer. |
| `COGNITO_SOURCE_GROUP` | Yes | Group whose members are the application user list. |
| `COGNITO_APP_CLIENT_ID` | Recommended | Enables JWT audience/client ID validation. |
| `COGNITO_ADMIN_GROUP` | No | Write-authorized group; default `rbac-admins`. |
| `SPRING_DATA_MONGODB_URI` | Yes | MongoDB connection URI. |
| `AUTHORIZATION_EVENTS_TOPIC_ARN` | No | Enables SNS outbox publishing. |
| `AUTHORIZATION_EVENTS_REGION` | No | SNS region; defaults to Cognito region. |

MongoDB must be a replica set or sharded cluster to support transactions.
Automatic index creation is enabled in the main configuration. Review indexes
and deploy them through your normal database change process for production.

The AWS identity used by this service needs Cognito read/write group and user
membership permissions. An SNS publisher additionally needs `sns:Publish` on
the configured topic.

## Code map

| Area | Location | Responsibility |
|---|---|---|
| REST endpoints | `controller/` | HTTP contract and authorization annotations. |
| Domain services | `service/` | Validation, integrations, transaction boundaries. |
| Permission model | `permission/` | Documents, repositories, statuses, role-key validation. |
| Audit | `audit/` | Context, audit records, search, MapStruct mapping. |
| Event outbox | `outbox/` | Durable events and optional SNS publishing. |
| AWS/security config | `config/` | Cognito JWT, AWS clients, properties, transactions. |
| Mapping | `mapper/` | MapStruct API/persistence mapping. |

## Error behavior and troubleshooting

The global error handler maps validation, not-found, conflict, Mongo
optimistic-locking, and integration failures to API errors. Conflict cases
include duplicate permission coordinates, assigning an inactive permission,
and deleting a role with active assignments.

Common deployment failures:

- **`MongoTransactionException` or transactions unavailable:** use a replica
  set/sharded MongoDB deployment, not standalone MongoDB.
- **401 JWT errors:** verify user pool, issuer, client ID, token type, and the
  server's ability to reach Cognito JWKS.
- **403 on writes:** the token must include the configured admin group in
  `cognito:groups`.
- **SNS pending events:** check the topic ARN, AWS region/credentials, and
  consumer-side event deduplication. Failed records retain attempts and error
  details for retry.
- **Role validation error:** use lowercase `module:role`.

## Testing

Run the complete unit/context suite with:

```bash
./mvnw test
```

Tests start the Spring context. In a restricted local sandbox, the MongoDB
driver may log that it cannot connect to `localhost:27017`; the suite can still
pass when tests do not require a live database. Use a replica-set MongoDB
instance for integration testing of transaction and repository behavior.
