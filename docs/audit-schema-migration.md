# Historical audit schema migration

This document is retained only for installations that already contain legacy
audit documents. The application contains no runtime Cognito-group or AVP-policy
compatibility implementation. Complete this migration before deploying the
version that removes the obsolete audit action enum values.

New audit documents use:

- `role_name` instead of `group_name`;
- `user_sub` instead of `actor_sub`; and
- `user_email` instead of `actor_email`.

The authenticated caller is represented by `user_sub` and `user_email`.
`changes.targetUserSub` identifies the affected user for user-role mutations.

## Migration procedure

Back up `audit_entries`, stop application writes, and run the following in
`mongosh` against the Nexus database:

First, preserve audit records for operations this service no longer owns. They
have no truthful one-to-one mapping to the current role model, so archive them
rather than relabeling or discarding their history:

```javascript
const legacyActions = [
  "GROUP_CREATED", "GROUP_UPDATED", "GROUP_DELETED",
  "USER_ADDED_TO_GROUP", "USER_REMOVED_FROM_GROUP",
  "USER_METADATA_UPDATED", "USER_ENABLED", "USER_DISABLED",
  "POLICY_CREATED", "POLICY_UPDATED", "POLICY_DELETED"
]

db.audit_entries.aggregate([
  { $match: { action: { $in: legacyActions } } },
  { $merge: { into: "audit_entries_legacy", whenMatched: "keepExisting", whenNotMatched: "insert" } }
])

const sourceCount = db.audit_entries.countDocuments({ action: { $in: legacyActions } })
const archiveCount = db.audit_entries_legacy.countDocuments({ action: { $in: legacyActions } })
if (archiveCount < sourceCount) {
  throw new Error("Legacy audit archive verification failed")
}

db.audit_entries.deleteMany({ action: { $in: legacyActions } })
```

Restrict `audit_entries_legacy` to compliance/audit readers; the application
does not query it. Then migrate the retained current-model documents:

```javascript
db.audit_entries.updateMany(
  {},
  [
    {
      $set: {
        role_name: { $ifNull: ["$role_name", "$group_name"] },
        user_sub: { $ifNull: ["$user_sub", "$actor_sub"] },
        user_email: { $ifNull: ["$user_email", "$actor_email"] }
      }
    }
  ]
)
```

Verify that all records that must be retained have `user_sub` and `user_email`.
Historical records with missing actor email require a trusted backfill decision;
do not invent an email value.

After verification, remove the legacy fields:

```javascript
db.audit_entries.updateMany(
  {},
  {
    $unset: {
      group_name: "",
      target_username: "",
      actor_sub: "",
      actor_email: "",
      actor_groups: ""
    }
  }
)
```

Restart the service with automatic index creation enabled and verify searches by
`roleName`, `userSub`, and `userEmail`.
