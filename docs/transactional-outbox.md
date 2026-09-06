# Transactional outbox and authorization invalidation events

## Purpose

This service does not manage sessions or make authorization decisions. It
publishes durable RBAC change notifications to the auth-service, which will
force affected sessions to refresh or log out. The separate authorization
decision service remains outside this project and will be implemented later.

The implementation uses MongoDB as a transactional outbox:

```text
RBAC mutation + audit entry + applicable outbox event
                    │
                    └── one MongoDB transaction

committed outbox event
        │ background dispatcher
        ▼
      Amazon SQS
        │
        ▼
auth-service
  ├── resolve affected active sessions
  └── force refresh or logout
```

The HTTP request never waits for SQS. For a session-affecting mutation, a
successful response means the RBAC mutation, audit entry, and durable outbox
record committed together. It does not mean SQS has already accepted the
message.

## Event contract

All events use `eventType=nexus.authorization.changed` and `schemaVersion=1`.

```json
{
  "eventId": "7f5df26d-0475-4bc3-a353-f14dc560f48a",
  "eventType": "nexus.authorization.changed",
  "schemaVersion": 1,
  "source": "nexus-rbac-admin",
  "occurredAt": "2026-09-06T10:00:00Z",
  "correlationId": "request-123",
  "aggregateType": "USER_ROLE",
  "aggregateId": "user-sub:role-id",
  "payload": {
    "changeType": "USER_ROLE_REMOVED",
    "deliverySemantics": "SESSION_INVALIDATION_SIGNAL",
    "impactScope": "USER",
    "sessionDirective": "FORCE_REFRESH_OR_LOGOUT",
    "roleId": "role-id",
    "roleKey": "billing:administrator",
    "targetUserSub": "user-sub"
  }
}
```

The message contains external identifiers only; MongoDB `_id`, JWTs, request
bodies, and user email are never included.

### Mutation matrix

| Change type | Impact scope | Session directive |
|---|---|---|
| `ROLE_ACTIVATED`, `ROLE_DEACTIVATED` | `ROLE` | `FORCE_REFRESH_OR_LOGOUT` |
| `USER_ROLE_ASSIGNED`, `USER_ROLE_REMOVED`, `USER_ROLE_RESTORED` | `USER` | `FORCE_REFRESH_OR_LOGOUT` |
| `PERMISSION_ACTIVATED`, `PERMISSION_DEACTIVATED` | `PERMISSION` | `FORCE_REFRESH_OR_LOGOUT` |
| `ROLE_PERMISSION_GRANTED`, `ROLE_PERMISSION_REVOKED`, `ROLE_PERMISSION_RESTORED` | `ROLE` | `FORCE_REFRESH_OR_LOGOUT` |

Role display metadata and permission descriptions do not change effective
authorization and therefore do not publish an invalidation event. Creating an
unassigned role or permission cannot affect an existing session, so those
operations are audited but also do not publish.

## Consumer requirements

Amazon SQS standard queues can deliver a message more than once and can deliver
messages out of order. The event is deliberately an invalidation signal, not a
delta. The auth-service must:

1. Deduplicate durably by `eventId`.
2. Never grant or revoke access by replaying `changeType` directly.
3. Persist an invalidation watermark for the indicated `USER`, `ROLE`, or
   `PERMISSION` scope before scanning sessions. Session creation/refresh must
   coordinate with that watermark so it cannot race with invalidation.
4. Find sessions through user/role/permission reverse indexes and mark them for
   forced refresh or logout. A later refresh obtains current permissions through
   the authorization path; this event is not an entitlement payload.
5. Delete the SQS message only after the deduplication record, watermark, and
   session state changes are durable.
6. Configure an SQS dead-letter queue and alarm on its visible-message count.

A FIFO queue is also supported. For a `.fifo` queue URL, the publisher supplies
the configured message group and uses `eventId` as the deduplication ID. The
consumer must remain idempotent because a publish acknowledgement can still be
lost outside the FIFO deduplication interval.

- [AWS SDK for Java 2.x SQS message example](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/examples-sqs-messages.html)
- [Amazon SQS standard queue delivery semantics](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/standard-queues.html)
- [Amazon SQS FIFO queues](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-fifo-queues.html)

## Delivery guarantees

`AuthorizationAuditAspect` requests emission only after a service mutation
succeeds. For a session-affecting change, the audit entry and `outbox_events`
document are written before the same MongoDB transaction commits. If either
write fails, the mutation rolls back.

The dispatcher:

- atomically leases one due event with `findAndModify`;
- recovers an abandoned `IN_PROGRESS` event after its lease expires;
- publishes outside the user request;
- retries with capped exponential backoff;
- marks exhausted events `FAILED` for operator review; and
- retains published rows for seven days through a MongoDB TTL index.

Delivery is at least once. If SQS accepts a message but the process cannot mark
the row published, the lease expires and the message is sent again. This is why
consumer deduplication is mandatory.

## Configuration

Local development disables the dispatcher in `application-local.yaml`.
Non-local environments enable it by default and fail startup when the queue URL
is absent.

```bash
export SPRING_PROFILES_ACTIVE=production
export AUTHORIZATION_EVENTS_QUEUE_URL='https://sqs.us-east-1.amazonaws.com/123456789012/nexus-authorization-changes'
export AUTHORIZATION_EVENTS_REGION='us-east-1'
```

The application IAM principal requires only `sqs:SendMessage`, scoped to the
configured queue. Queue encryption, consumer permissions, visibility timeout,
redrive policy, and the dead-letter queue belong in infrastructure configuration.
The default overall publish timeout is 10 seconds, below the 30-second lease;
both values are configurable under `messaging.outbox`.

Set `AUTHORIZATION_EVENTS_ENABLED=false` only for local development, tests, or
an explicitly controlled maintenance window. Disabling it means new mutations
will not create outbox events.

## Operations

Alert when a `PENDING` event's `createdAt` is older than the delivery SLO or any
event reaches `FAILED`. Structured log event names are:

- `outbox_event_enqueued`
- `outbox_event_published`
- `outbox_event_retry_scheduled`
- `outbox_event_failed`
- `outbox_publish_lease_lost`
- `outbox_dispatch_cycle_failed`

After correcting the underlying problem, a failed row can be replayed by
setting `status=PENDING`, `attempts=0`, and `nextAttemptAt` to the current time.
Keep the same `eventId` so the consumer can deduplicate it.

## Reuse in another service

The `messaging.outbox` package contains no RBAC types. Another MongoDB-backed
service can reuse the pattern by:

1. Depending on `TransactionalOutbox`.
2. Creating an `OutboxMessage` with its own event type and payload.
3. Calling `enqueue` inside its existing MongoDB transaction.
4. Reusing `MongoOutboxStore`, `OutboxDispatcher`, and `SqsMessagePublisher`.

Only `AuthorizationChangePublisher` is RBAC-specific. If multiple repositories
need the implementation, move `messaging.outbox` into an internal Spring Boot
starter without moving domain event mappers into that starter.
