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

The service does not make application authorization decisions. A separate
authorization service will be implemented later. Until it is deployed, this
administrative API must not be exposed directly to end users.

## Documentation

- [Nexus role model](docs/nexus-role-model.md) — collections, indexes, API,
  aggregation design, declarative audit, and migration guidance.
- [AI agent guide](docs/ai-agent-guide.md) — invariants and safe change rules.
- [Observability and errors](docs/observability.md) — CloudWatch JSON fields,
  Logs Insights queries, correlation, and the API error contract.
- [Transactional outbox](docs/transactional-outbox.md) — authorization-change
  event contract, SQS delivery, retries, and auth-service consumer rules.

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

`local` is the default Spring profile and uses readable console logging. Set a
non-local profile such as `SPRING_PROFILES_ACTIVE=production` in deployments to
enable newline-delimited JSON logging for CloudWatch.

Non-local profiles also enable durable authorization-change propagation. Set
`AUTHORIZATION_EVENTS_QUEUE_URL` to the auth-service SQS queue; startup fails
when propagation is enabled without a queue URL. The local profile disables the
outbox dispatcher.

Run verification:

```bash
./mvnw test
```

`GET /actuator/health` is public. Other routes require a Cognito access token
with `sub`, `email`, and the configured client ID. Authentication is not
administrative authorization: use private ingress now, and require the future
authorization service to protect all administrative routes before production
traffic reaches this application.

User-role assignment accepts at most 50 canonical Cognito `sub` UUIDs. Each
distinct subject is resolved by exact `sub` search and confirmed through
`AdminGetUser` before the MongoDB mutation/audit transaction begins; an unknown
subject returns `404`, malformed input returns `400`, and an unavailable or
inconsistent Cognito dependency returns `502`.
