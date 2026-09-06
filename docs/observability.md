# Observability and API errors

## Runtime logging contract

Production logs are newline-delimited JSON on standard output. ECS `awslogs`,
Fluent Bit on EKS, or the CloudWatch agent should ship stdout to CloudWatch;
the application does not call `PutLogEvents` directly. The default Spring
profile is `local`, which uses readable console logs and disables structured
JSON. Any active profile that does not include `local` uses structured JSON.

For a deployed environment, explicitly activate a non-local profile and set
the environment label included in each JSON event:

```bash
export SPRING_PROFILES_ACTIVE=production
export DEPLOYMENT_ENVIRONMENT=production
export APPLICATION_LOG_LEVEL=INFO
```

For local development, no profile setting is required. To select it explicitly:

```bash
export SPRING_PROFILES_ACTIVE=local
export DEPLOYMENT_ENVIRONMENT=local
```

Do not combine `local` with a deployment profile because the local appender
takes precedence by disabling the non-local profile expression. Every non-local
JSON event includes UTC `timestamp`, `schemaVersion`, `service`, `environment`,
`level`, `logger`, and `message`.

`RequestLoggingFilter` adds one completion event per request:

```json
{
  "event": "http_request_completed",
  "requestId": "d4460acc-a83e-4888-a692-735de3b740a8",
  "correlationId": "d4460acc-a83e-4888-a692-735de3b740a8",
  "traceId": "1-66db08f0-0123456789abcdef01234567",
  "httpMethod": "POST",
  "httpRoute": "/api/v1/roles/{roleId}/activate",
  "httpStatus": 200,
  "outcome": "SUCCESS",
  "durationMs": 24,
  "userSub": "cognito-sub"
}
```

- A valid inbound `X-Correlation-Id` becomes `requestId`; otherwise the service
  generates a UUID. Every response returns it as `X-Request-Id`.
- `traceId` is the validated `Root` value from `X-Amzn-Trace-Id` when present.
- Handler route templates are logged instead of concrete path variables to
  reduce cardinality and avoid placing usernames or user subjects in paths.
- Successful health-probe completion is DEBUG to reduce CloudWatch ingestion.
- Request bodies, JWTs, authorization headers, and user email are never written
  to operational logs. Email remains in the durable audit document as required.

Committed audit mutations produce `authorization_audit_committed`. This event
is registered with Mongo transaction synchronization and appears only after the
business mutation and audit entry commit successfully.

Server failures produce one `application_error` event with a shortened stack
trace. Cognito failures also include `operation`, `awsStatusCode`,
`awsErrorCode`, and `awsRequestId`. Service layers wrap failures but do not log
them again, preventing duplicate stack traces.

## CloudWatch Logs Insights examples

Recent server failures:

```text
fields @timestamp, service, environment, errorCode, component, requestId, message
| filter event = "application_error"
| sort @timestamp desc
| limit 100
```

Slow routes and p95 latency:

```text
filter event = "http_request_completed"
| stats count(*) as requests, pct(durationMs, 95) as p95,
        max(durationMs) as maxDuration by httpMethod, httpRoute
| sort p95 desc
```

Follow one request:

```text
fields @timestamp, level, event, message, errorCode, auditAction
| filter requestId = "d4460acc-a83e-4888-a692-735de3b740a8"
| sort @timestamp asc
```

Cognito dependency failures:

```text
fields @timestamp, operation, awsStatusCode, awsErrorCode, awsRequestId, requestId
| filter event = "application_error" and component = "cognito"
| sort @timestamp desc
```

Consider CloudWatch field indexes for `event`, `requestId`, and `errorCode` on
high-volume Standard log groups. Do not use high-cardinality values such as
`requestId`, `userSub`, role IDs, or permission IDs as metric dimensions.

## API error contract

Controller errors and Spring Security 401/403 failures return the same JSON:

```json
{
  "timestamp": "2026-09-06T14:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "path": "/api/v1/roles",
  "requestId": "d4460acc-a83e-4888-a692-735de3b740a8",
  "violations": [
    {"field": "displayName", "message": "must not be blank"}
  ]
}
```

The `code` is stable for clients; `message` is human-readable. Internal,
MongoDB, audit, and Cognito exception details are never returned. All error
responses use `Cache-Control: no-store`. A corresponding completion event uses
the same `requestId` and `errorCode`.

| Code | HTTP status |
|---|---:|
| `AUTHENTICATION_REQUIRED` | 401 |
| `ACCESS_DENIED` | 403 |
| `RESOURCE_NOT_FOUND` | 404 |
| `RESOURCE_CONFLICT`, `CONCURRENT_MODIFICATION` | 409 |
| `VALIDATION_FAILED`, `MALFORMED_REQUEST`, `MISSING_REQUIRED_VALUE` | 400 |
| `METHOD_NOT_ALLOWED` | 405 |
| `NOT_ACCEPTABLE` | 406 |
| `UNSUPPORTED_MEDIA_TYPE` | 415 |
| `UPSTREAM_IDENTITY_PROVIDER_ERROR` | 502 |
| `DATA_STORE_UNAVAILABLE`, `AUDIT_UNAVAILABLE` | 503 |
| `INTERNAL_ERROR` | 500 |

CloudWatch automatically discovers top-level JSON fields for Logs Insights.
Embedded Metric Format is intentionally not emitted by this service; add EMF
only for a small, reviewed set of low-cardinality metrics and dimensions.
