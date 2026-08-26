# Cognito RBAC Service

Administration service for the authorization-source data used by Nexus
applications. It manages Cognito users and role membership, then maintains
reusable permissions and role-to-permission assignments in MongoDB.

The current model is:

```text
User (Cognito) ──member of──> Role (Cognito group) ──granted──> Permission (MongoDB)
```

A role is a Cognito group named `module:role`, for example
`deliveryops:developer`. A permission is an immutable coordinate such as
`deliveryops:stores:write`. The service does not create an AVP policy when a
role, permission, or assignment changes; AVP policies are reusable static
infrastructure.

## Documentation

- [Project guide](docs/project-guide.md) — setup, API usage, data ownership,
  operations, and troubleshooting for engineers and operators.
- [AI agent guide](docs/ai-agent-guide.md) — architecture, invariants,
  change workflows, and verification rules for coding agents.
- [Role-permission migration](docs/role-permission-migration.md) — cutover
  scope and compatibility boundary.
- [Legacy AVP reference](docs/avp-schema-and-policies.md) — historical
  three-part group/policy implementation; not the model for new features.

## Quick start

### Prerequisites

- Java 21
- MongoDB configured as a replica set (transactions are required for
  permission and role-permission mutations)
- AWS credentials supplied through the standard AWS SDK provider chain
- A Cognito user pool and app client

Set the minimum configuration in your environment:

```bash
export COGNITO_REGION=us-east-1
export COGNITO_USER_POOL_ID=us-east-1_example
export COGNITO_APP_CLIENT_ID=example-client-id
export COGNITO_SOURCE_GROUP=portal-users
export COGNITO_ADMIN_GROUP=rbac-admins
export SPRING_DATA_MONGODB_URI='mongodb://localhost:27017/cognito_rbac?replicaSet=rs0'
```

Run locally:

```bash
./mvnw spring-boot:run
```

Run tests:

```bash
./mvnw test
```

Health is available without authentication at `GET /actuator/health`.
All other routes require a valid Cognito JWT.

## API essentials

Use an access token and provide a reason for every write:

```bash
export TOKEN='<Cognito access token>'
export AUDIT_REASON='Initial role configuration'
```

```bash
curl -X POST http://localhost:8080/api/v1/permissions \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "X-Audit-Reason: $AUDIT_REASON" \
  -d '{
    "module": "deliveryops",
    "resourceType": "stores",
    "access": "write",
    "description": "Create and update stores"
  }'
```

The response includes a UUID `permissionId`. It never includes MongoDB's
internal `_id`.

```bash
curl -X POST "http://localhost:8080/api/v1/groups/deliveryops:developer/permissions/$PERMISSION_ID" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Audit-Reason: $AUDIT_REASON"
```

See the [project guide](docs/project-guide.md#http-api) for the complete API.

## Authorization boundary

- **Cognito** is authoritative for users, roles, and user-to-role membership.
- **MongoDB** is authoritative for permissions, role-permission assignments,
  audit records, and the delivery outbox.
- **SNS**, when configured, receives asynchronous invalidation events. Its
  consumers must de-duplicate using `eventId` because delivery is at-least-once.
- **AWS Verified Permissions** remains available for the legacy policy APIs,
  but no new role-permission lifecycle operation writes an AVP policy.

## Security and audit

Current role and permission write endpoints require membership in
`cognito.admin-group` (default `rbac-admins`) and an `X-Audit-Reason` header.
`X-Correlation-Id` is optional; when absent, the service creates one and
returns it as `X-Request-Id`.

Permission and role-permission writes persist the source change, an audit entry,
and an outbox event in the same MongoDB transaction. Read operations are not
audited.
