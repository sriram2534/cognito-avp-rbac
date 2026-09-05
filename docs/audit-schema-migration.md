# Audit schema migration

New audit documents use:

- `role_name` instead of `group_name`;
- `user_sub` instead of `actor_sub`; and
- `user_email` instead of `actor_email`.

The authenticated caller is represented by `user_sub` and `user_email`.
`changes.targetUserSub` identifies the affected user for user-role mutations.

## Migration procedure

Back up `audit_entries`, stop application writes, and run the following in
`mongosh` against the Nexus database:

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
