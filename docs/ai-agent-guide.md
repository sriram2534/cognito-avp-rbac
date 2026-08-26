# AI agent guide

This guide is the implementation contract for an AI agent or engineer changing
this repository. Treat the current Java code as authoritative when it differs
from documentation.

## Mission and architecture

This Spring Boot 4 / Java 21 service manages authorization-source data:

```text
Cognito User ──member of──> Cognito Role (`module:role`)
                                  │
                                  └── MongoDB role_permissions ──> MongoDB Permission

Permission/assignment change
  -> Mongo transaction: source document + audit_entries + authorization_outbox
  -> scheduled SNS publishing when a topic is configured
  -> consumer invalidates its effective-permission cache
```

The runtime authorization decision belongs to the downstream authorization
service. This project is the source-data and invalidation-event producer.

## Non-negotiable invariants

1. **Cognito is authoritative for roles and membership.** A role assignment
   must refer to an existing Cognito group; validate it with
   `GroupService.requireRole` before creating a new assignment.
2. **A role key is exactly lowercase `<module>:<role>`.** Use
   `RoleKey.requireCanonical`; do not duplicate or loosen the validation.
3. **A permission's external identity is `permissionId` (UUID).** MongoDB `_id`
   is persistence-only. Never expose `_id` in an API response and never use it
   as a foreign key.
4. **Permission coordinates are immutable.** `module`, `resourceType`, and
   `access` form the semantic identity. Permit only description/status changes.
5. **`role_permissions` is unique by `(roleKey, permissionId)`.** Revocation is
   history (`REVOKED`); re-grant restores the existing document.
6. **Permission and relationship writes are atomic with audit and outbox
   writes.** Keep their mutation paths `@Transactional`. Production MongoDB
   must be a replica set or sharded cluster.
7. **Audit only mutations.** `GET` endpoints must not create audit entries.
   Keep reason, actor, field changes, and correlation ID intact for writes.
8. **Outbox delivery is at-least-once.** Do not assume exactly-once delivery;
   consumers deduplicate SNS messages using `eventId`.
9. **Do not add runtime authorization decisions here.** Preserve the clear
   producer/consumer boundary with the downstream authorization service.
10. **MapStruct owns DTO/entity conversion.** Add or update mapper methods;
    do not manually construct DTOs or persistence objects in services.

## Before changing code

1. Read [project-guide.md](project-guide.md) and
   [role-permission-migration.md](role-permission-migration.md).
2. Locate the existing vertical slice: controller, service, domain document,
   repository, mapper, audit action, and outbox contract.
3. Check for existing unrelated work with `git status --short`; preserve it.
4. Use `rg` for code search. Make focused edits and preserve unrelated work.

## Change recipes

### Add a permission field

1. Decide whether it is immutable identity or mutable metadata. Do not change
   coordinates after creation.
2. Update `Permission`, index strategy if necessary, request/response DTOs,
   and `PermissionMapper`.
3. If a mutation is exposed, update `PermissionService` within the existing
   transaction, audit an explicit before/after `FieldChange`, and publish the
   appropriate invalidation event.
4. Add validation and tests. Do not add Mongo `_id` to a public DTO.

### Add a role-permission lifecycle operation

1. Canonicalize the role with `RoleKey.requireCanonical`.
2. Confirm the Cognito role exists and the referenced permission exists.
3. Enforce status/uniqueness semantics through `RolePermissionService` and its
   repository.
4. Use `RolePermissionMapper` for mapping/construction.
5. Persist a `ROLE_PERMISSION_*` audit action and enqueue
   `ROLE_PERMISSIONS_CHANGED` in the same transaction.
6. Preserve idempotency: revoking an already-revoked assignment is a no-op;
   granting an active assignment is a conflict; granting a revoked assignment
   restores it.

### Add an auditable mutation

1. Add an `AuditAction` only if no existing action precisely fits.
2. Build field changes with `FieldChangeMapper`, not manual constructors.
3. Use `AuditService.recordAuthorizationChange` for permission/relationship
   source mutations. It is intentionally called by the service rather than
   `AuditAspect` to join the same Mongo transaction as source and outbox data.
4. Ensure the controller write requires `X-Audit-Reason`.
   `AuditContextFilter` supplies actor and correlation information for audited
   route prefixes.
5. Do not add audit behavior to reads.

### Add or change an outbox event

1. Keep a stable, explicit `eventType` and `eventVersion` in the payload.
2. Include an identifier clients can use to invalidate efficiently and the
   correlation ID/occurred timestamp.
3. Update the project guide's event table and tell downstream consumers of any
   breaking contract change.
4. Do not publish directly inside the mutation service; call
   `AuthorizationOutboxService.enqueue` so the event is durable with the
   transaction.

### Change an API response

1. Update the record in `dto/` and the MapStruct mapper in `mapper/` together.
2. Keep MongoDB identifiers out of all response bodies, including audit API
   serialization.
3. Preserve UUID `permissionId` and the composite external role-permission
   identity where required.
4. Update examples and endpoint documentation in `project-guide.md`.

## Key implementation locations

| Path | Change when |
|---|---|
| `permission/Permission.java` | Permission document, indexes, lifecycle state. |
| `permission/RolePermission.java` | Assignment document/indexes/state. |
| `permission/RoleKey.java` | Canonical role syntax only. |
| `service/PermissionService.java` | Permission business rules, audit, outbox. |
| `service/RolePermissionService.java` | Assignment business rules, audit, outbox. |
| `service/GroupService.java` | Cognito roles/membership and membership events. |
| `mapper/PermissionMapper.java` | Permission mapping/construction. |
| `mapper/RolePermissionMapper.java` | Assignment mapping/construction. |
| `audit/AuditService.java` | Audit persistence semantics. |
| `audit/AuditAspect.java` | Legacy group/user mutation audit only. |
| `outbox/AuthorizationOutboxPublisher.java` | Asynchronous SNS delivery behavior. |
| `config/SecurityConfig.java` | JWT/security filter rules. |
| `config/AuthorizationEventsConfig.java` | Transaction manager and optional SNS client. |

## Verification checklist

Before handing off a change:

```bash
./mvnw test
git diff --check
```

Also verify, in proportion to the change:

- Response payloads do not contain Mongo `_id`.
- New current-RBAC writes require authorization and `X-Audit-Reason`.
- New mutable source changes have audit action, useful field deltas, and an
  outbox effect if downstream authorization state can change.
- Transactional source changes still include audit and outbox writes.
- User-facing docs reflect changed API and event contracts.
