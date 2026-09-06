# Nexus RBAC Administration Service

This service owns Nexus authorization-source data. Cognito supplies identity
only; its immutable `sub` claim is the user ID. Nexus roles, user-role
membership, permissions, and role-permission assignments live in MongoDB.

```text
Cognito user (`sub`)
  -> nexus_user_roles
  -> nexus_roles
  -> nexus_role_permissions
  -> nexus_permissions
```

The service does not make application authorization decisions. The external
authorization service enforces access at the API gateway or service boundary.

## Documentation

- [Nexus role model](docs/nexus-role-model.md) — collections, indexes, API,
  aggregation design, declarative audit, and migration guidance.
- [AI agent guide](docs/ai-agent-guide.md) — invariants and safe change rules.

## Run locally

Prerequisites: Java 21, MongoDB replica set, AWS credentials with
`cognito-idp:ListUsers` and `cognito-idp:AdminGetUser`, and a Cognito user pool
whose access tokens include the `email` claim.

```bash
export COGNITO_REGION=us-east-1
export COGNITO_USER_POOL_ID=us-east-1_example
export COGNITO_APP_CLIENT_ID=example-client-id
export SPRING_DATA_MONGODB_URI='mongodb://localhost:27017/nexus_rbac?replicaSet=rs0'
./mvnw spring-boot:run
```

Run verification:

```bash
./mvnw test
```

`GET /actuator/health` is public. Other routes require a Cognito access token
with `sub`, `email`, and the configured client ID. The external authorization
service must protect administrative access before traffic reaches this
application.

User-role assignment accepts at most 50 canonical Cognito `sub` UUIDs. Each
distinct subject is resolved by exact `sub` search and confirmed through
`AdminGetUser` before the MongoDB mutation/audit transaction begins; an unknown
subject returns `404`, malformed input returns `400`, and an unavailable or
inconsistent Cognito dependency returns `502`.
