# AWS Verified Permissions — Schema & Policies Reference

## Overview

- **Cognito User Pool** issues ID/Access tokens with `cognito:groups` claim
- **Group naming convention:** `module:resource:access`
- **AVP** evaluates policies at runtime to ALLOW or DENY requests
- **Admin role** only exists at module level (`module:global:admin`)

## Project Structure (AVP Integration)

```
src/main/java/com/designpattern/cognitorbac/
├── avp/
│   ├── CedarPolicyBuilder.java       ← Generates Cedar policy statements
│   ├── GroupNameParser.java           ← Parses module:resource:access groups
│   └── SecurityContextHelper.java    ← Extracts caller info from JWT
├── config/
│   ├── VerifiedPermissionsConfig.java ← AVP client bean
│   └── VerifiedPermissionsProperties.java ← AVP config properties
├── controller/
│   ├── PolicyController.java          ← CRUD for policies
│   └── SchemaController.java          ← Schema management + bootstrap
├── dto/avp/
│   ├── AddActionRequest.java          ← Add action to schema
│   ├── AuthorizationRequest.java      ← Test authorization
│   ├── AuthorizationResponse.java     ← Authorization result
│   ├── CreatePolicyRequest.java       ← Create policy input
│   ├── PolicyResponse.java            ← Policy output
│   ├── SchemaResponse.java            ← Schema summary output
│   └── UpdatePolicyRequest.java       ← Update policy input
├── service/
│   ├── AuthorizationService.java      ← Runtime IsAuthorizedWithToken
│   ├── PolicyService.java             ← Policy CRUD + authorization checks
│   ├── PolicyStoreBootstrapService.java ← One-time setup
│   └── SchemaService.java             ← Schema read/write/add actions
└── resources/cedar/
    └── schema.json                    ← Base Cedar schema template
```

---

## Group Naming Convention

| Format | Example | Meaning |
|--------|---------|---------|
| `global:global:admin` | Super admin | Full access to everything |
| `module:global:admin` | `ops:global:admin` | Full access within ops module |
| `module:resource:write` | `ops:store:write` | Write+Read on specific resource |
| `module:resource:read` | `ops:store:read` | Read only on specific resource |

### Valid Groups

```
global:global:admin           → super admin (full portal access)
ops:global:admin              → ops module admin (all ops resources)
ops:store:write               → write+read on ops/store
ops:store:read                → read only on ops/store
ops:stores_publish:write      → write+read on ops/stores_publish
ops:stores_publish:read       → read only on ops/stores_publish
subscription:global:admin     → subscription module admin
subscription:incentive:write  → write+read on subscription/incentive
subscription:incentive:read   → read only on subscription/incentive
```

### Rules

- `resource = "global"` → access to ALL resources in that module
- `resource = specific name` → access to only that resource
- `access = admin` → only valid when resource = "global" (module-level admin only)
- `access = write` → write + read (via action hierarchy)
- `access = read` → read only

---

## Action Hierarchy

```
Admin
  └── includes Write
        └── includes Read
```

- Permitting `Admin` → user can also do `Write` and `Read`
- Permitting `Write` → user can also do `Read`
- Permitting `Read` → user can only `Read`

---

## HTTP Method → Action Mapping

| HTTP Method | Action to pass to AVP |
|-------------|----------------------|
| `GET`       | `"Read"`             |
| `POST`      | `"Write"`            |
| `PUT`       | `"Write"`            |
| `PATCH`     | `"Write"`            |
| `DELETE`    | `"Admin"`            |

---

## URL Pattern → Resource Mapping

| URL Pattern | module | resourceType |
|-------------|--------|--------------|
| `/api/v1/ops/store/**` | `ops` | `store` |
| `/api/v1/ops/stores_publish/**` | `ops` | `stores_publish` |
| `/api/v1/subscription/incentive/**` | `subscription` | `incentive` |

---

## Cedar Schema (JSON)

Used with the `PutSchema` API. Defines entity types (User, Group, Resource) and actions (Read, Write, Admin).

```json
{
  "Portal": {
    "entityTypes": {
      "User": {
        "memberOfTypes": ["Group"],
        "shape": {
          "type": "Record",
          "attributes": {
            "sub": {
              "type": "String",
              "required": true
            },
            "email": {
              "type": "String",
              "required": true
            }
          }
        }
      },
      "Group": {
        "memberOfTypes": ["Group"],
        "shape": {
          "type": "Record",
          "attributes": {
            "module": {
              "type": "String",
              "required": true
            },
            "resource": {
              "type": "String",
              "required": true
            },
            "access": {
              "type": "String",
              "required": true
            }
          }
        }
      },
      "Resource": {
        "memberOfTypes": [],
        "shape": {
          "type": "Record",
          "attributes": {
            "module": {
              "type": "String",
              "required": true
            },
            "resourceType": {
              "type": "String",
              "required": true
            }
          }
        }
      }
    },
    "actions": {
      "Read": {
        "memberOf": [],
        "appliesTo": {
          "principalTypes": ["User", "Group"],
          "resourceTypes": ["Resource"],
          "context": {
            "type": "Record",
            "attributes": {}
          }
        }
      },
      "Write": {
        "memberOf": [
          { "id": "Read", "type": "Portal::Action" }
        ],
        "appliesTo": {
          "principalTypes": ["User", "Group"],
          "resourceTypes": ["Resource"],
          "context": {
            "type": "Record",
            "attributes": {}
          }
        }
      },
      "Admin": {
        "memberOf": [
          { "id": "Write", "type": "Portal::Action" }
        ],
        "appliesTo": {
          "principalTypes": ["User", "Group"],
          "resourceTypes": ["Resource"],
          "context": {
            "type": "Record",
            "attributes": {}
          }
        }
      }
    }
  }
}
```

### Schema Notes

- **Actions are NOT created separately** — they live inside the schema
- **Resources are NOT pre-registered** — your app constructs them at runtime
- **`memberOf` on actions** creates the hierarchy (Write includes Read, Admin includes Write)
- **`memberOfTypes: ["Group"]` on User** means a user can belong to groups
- **`memberOfTypes: ["Group"]` on Group** means groups can be nested

---

## Cedar Policies

### Policy 1: Super Admin — full unrestricted access

```cedar
// Any user in global:global:admin can perform any action on any resource.
// No conditions, no restrictions.
permit (
    principal in Portal::Group::"global:global:admin",
    action,
    resource
);
```

---

### Policy 2: Ops Module Admin — all actions, ops module only

```cedar
// ops:global:admin can do anything (Read, Write, Admin) on any resource
// as long as resource belongs to ops module.
permit (
    principal in Portal::Group::"ops:global:admin",
    action,
    resource
) when {
    resource.module == "ops"
};
```

---

### Policy 3: Subscription Module Admin — all actions, subscription module only

```cedar
// subscription:global:admin can do anything on any resource
// as long as resource belongs to subscription module.
permit (
    principal in Portal::Group::"subscription:global:admin",
    action,
    resource
) when {
    resource.module == "subscription"
};
```

---

### Policy 4: Ops Store Write — write+read, ops/store only

```cedar
// ops:store:write can Write (which includes Read via hierarchy)
// only on resources with module=ops AND resourceType=store.
permit (
    principal in Portal::Group::"ops:store:write",
    action in [Portal::Action::"Write"],
    resource
) when {
    resource.module == "ops" && resource.resourceType == "store"
};
```

---

### Policy 5: Ops Store Read — read only, ops/store only

```cedar
// ops:store:read can only Read
// only on resources with module=ops AND resourceType=store.
permit (
    principal in Portal::Group::"ops:store:read",
    action in [Portal::Action::"Read"],
    resource
) when {
    resource.module == "ops" && resource.resourceType == "store"
};
```

---

### Policy 6: Ops Stores Publish Write

```cedar
// ops:stores_publish:write can Write (includes Read)
// only on resources with module=ops AND resourceType=stores_publish.
permit (
    principal in Portal::Group::"ops:stores_publish:write",
    action in [Portal::Action::"Write"],
    resource
) when {
    resource.module == "ops" && resource.resourceType == "stores_publish"
};
```

---

### Policy 7: Ops Stores Publish Read

```cedar
// ops:stores_publish:read can only Read
// only on resources with module=ops AND resourceType=stores_publish.
permit (
    principal in Portal::Group::"ops:stores_publish:read",
    action in [Portal::Action::"Read"],
    resource
) when {
    resource.module == "ops" && resource.resourceType == "stores_publish"
};
```

---

### Policy 8: Subscription Incentive Write

```cedar
permit (
    principal in Portal::Group::"subscription:incentive:write",
    action in [Portal::Action::"Write"],
    resource
) when {
    resource.module == "subscription" && resource.resourceType == "incentive"
};
```

---

### Policy 9: Subscription Incentive Read

```cedar
permit (
    principal in Portal::Group::"subscription:incentive:read",
    action in [Portal::Action::"Read"],
    resource
) when {
    resource.module == "subscription" && resource.resourceType == "incentive"
};
```

---

### Policy 10: Forbid — Module admins cannot access other modules

```cedar
// Ops module admin is explicitly denied access to anything outside ops.
// "unless" means: this forbid applies UNLESS the condition is true.
// So: deny everything that is NOT ops.
forbid (
    principal in Portal::Group::"ops:global:admin",
    action,
    resource
) unless {
    resource.module == "ops"
};
```

```cedar
// Subscription module admin is explicitly denied outside subscription.
forbid (
    principal in Portal::Group::"subscription:global:admin",
    action,
    resource
) unless {
    resource.module == "subscription"
};
```

---

## Access Matrix

| Group | ops/store R | ops/store W | ops/sp R | ops/sp W | sub/inc R | sub/inc W |
|-------|:-----------:|:-----------:|:--------:|:--------:|:---------:|:---------:|
| `global:global:admin` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `ops:global:admin` | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ |
| `subscription:global:admin` | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ |
| `ops:store:write` | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| `ops:store:read` | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| `ops:stores_publish:write` | ❌ | ❌ | ✅ | ✅ | ❌ | ❌ |
| `ops:stores_publish:read` | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ |
| `subscription:incentive:write` | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ |
| `subscription:incentive:read` | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ |

> `sp` = stores_publish, `inc` = incentive

---

## Identity Source Configuration

| Setting | Value |
|---------|-------|
| User Pool ARN | `arn:aws:cognito-idp:<region>:<account>:userpool/<pool-id>` |
| App Client IDs | Your app client ID(s) |
| Principal Entity Type | `Portal::User` |
| Group Entity Type | `Portal::Group` |
| Group Claim | `cognito:groups` (default from Cognito) |

AVP automatically maps:
- Token `sub` → `Portal::User::"<sub-value>"`
- Token `cognito:groups: ["ops:store:write"]` → user is `in Portal::Group::"ops:store:write"`

---

## Setup Order (One-Time)

```
1. CreatePolicyStore         → creates empty policy store
2. PutSchema                 → uploads the JSON schema above (defines actions + entity types)
3. CreateIdentitySource      → links Cognito User Pool to the policy store
4. CreatePolicy (× N)       → creates each policy listed above
```

---

## Adding New Resources (Future)

When adding a new resource (e.g., `ops:returns:write`):

1. **Create group in Cognito** → `ops:returns:write`
2. **Create ONE policy in AVP:**
   ```cedar
   permit (
       principal in Portal::Group::"ops:returns:write",
       action in [Portal::Action::"Write"],
       resource
   ) when {
       resource.module == "ops" && resource.resourceType == "returns"
   };
   ```
3. **No schema changes needed** — Read, Write, Admin actions already cover everything
4. `ops:global:admin` automatically gets access (their policy has no `resourceType` filter)

---

## Runtime Flow

```
1. User sends request with Cognito ID token in Authorization header
2. Your app extracts: action (from HTTP method) + resource (from URL path)
3. Your app calls AVP: IsAuthorizedWithToken(token, action, resource)
4. AVP decodes token → gets user's groups → evaluates all matching policies
5. Returns ALLOW or DENY
6. Your app proceeds or returns 403
```

---

## Key Concepts Quick Reference

| Term | Meaning |
|------|---------|
| `permit` | ALLOW rule |
| `forbid` | DENY rule (always overrides permit) |
| `principal` | WHO (the user/group) |
| `action` | WHAT they're doing (Read/Write/Admin) |
| `resource` | ON WHAT (module + resourceType) |
| `when { ... }` | Extra conditions that must be TRUE |
| `unless { ... }` | Forbid applies UNLESS condition is true |
| `action` (alone) | Any action (wildcard) |
| `resource` (alone) | Any resource (filter in `when` clause) |
| `action in [...]` | Action must be one of the listed items |
| `==` | Exact match |
| `&&` | Logical AND |
| Default behavior | If no policy matches → DENY |

---

## API Endpoints

### Schema Management (`/api/v1/schema`)

| Method | Endpoint | Who | Description |
|--------|----------|-----|-------------|
| `GET` | `/api/v1/schema` | Any authenticated | Get schema summary (actions, entity types) |
| `GET` | `/api/v1/schema/raw` | Super admin | Get raw Cedar schema JSON |
| `PUT` | `/api/v1/schema` | Super admin | Upload/replace base schema |
| `POST` | `/api/v1/schema/actions` | Super admin | Add a new action to schema |
| `POST` | `/api/v1/schema/bootstrap` | Super admin | One-time: create policy store + schema + identity source |

### Policy Management (`/api/v1/policies`)

| Method | Endpoint | Who | Description |
|--------|----------|-----|-------------|
| `GET` | `/api/v1/policies` | Any authenticated | List all policies |
| `GET` | `/api/v1/policies/{id}` | Any authenticated | Get policy detail |
| `POST` | `/api/v1/policies` | Admin (super or module) | Create policy for a group |
| `POST` | `/api/v1/policies/module-admin/{module}` | Super admin only | Create module admin policies (permit+forbid pair) |
| `POST` | `/api/v1/policies/auto-create?groupName=x` | Admin (super or module) | Auto-generate policy from group name |
| `PUT` | `/api/v1/policies` | Admin (super or module) | Update policy statement |
| `DELETE` | `/api/v1/policies/{id}` | Super admin only | Delete a policy |
| `POST` | `/api/v1/policies/evaluate` | Any authenticated | Test authorization decision |

---

## Workflow: Setting Up a New Module

**Example:** Adding a `banners` module with `banners:global:admin` and `banners:hero:write`/`banners:hero:read`

### Step 1: Super admin creates module admin (one-time)

```bash
# Creates the Cognito group
POST /api/v1/groups
{ "groupName": "banners:global:admin", "description": "Banners module admin" }

# Creates the permit + forbid policy pair in AVP
POST /api/v1/policies/module-admin/banners
# Response: two policies created
```

### Step 2: Module admin creates resource groups

```bash
# banners:global:admin creates groups within their module
POST /api/v1/groups
{ "groupName": "banners:hero:write", "description": "Write access to hero banners" }

# Auto-create the policy in AVP
POST /api/v1/policies/auto-create?groupName=banners:hero:write
# Generates: permit(banners:hero:write, Write, resource) when { module=="banners" && resourceType=="hero" }

POST /api/v1/groups
{ "groupName": "banners:hero:read", "description": "Read access to hero banners" }

POST /api/v1/policies/auto-create?groupName=banners:hero:read
# Generates: permit(banners:hero:read, Read, resource) when { module=="banners" && resourceType=="hero" }
```

### Step 3: Module admin adds users to groups

```bash
# Add a user to the banners:hero:write group
POST /api/v1/groups/banners:hero:write/users
{ "usernames": ["user1", "user2"] }
```

### Step 4: Adding a new access level (e.g., Publish)

```bash
# Super admin adds "Publish" action to schema (only needed once, not per module)
POST /api/v1/schema/actions
{ "actionName": "Publish", "memberOf": ["Read"], "description": "Publish content" }

# Now module admin can create a publish group
POST /api/v1/groups
{ "groupName": "banners:hero:publish" }

POST /api/v1/policies/auto-create?groupName=banners:hero:publish
# Generates: permit(banners:hero:publish, Publish, resource) when { module=="banners" && resourceType=="hero" }
```

---

## Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `COGNITO_REGION` | Yes | AWS region (e.g. `us-east-1`) |
| `COGNITO_USER_POOL_ID` | Yes | Cognito User Pool ID |
| `COGNITO_APP_CLIENT_ID` | Yes | Cognito App Client ID |
| `COGNITO_USER_POOL_ARN` | Yes (bootstrap) | Full ARN of the Cognito User Pool |
| `AVP_POLICY_STORE_ID` | Yes (after bootstrap) | AVP Policy Store ID |
| `AVP_REGION` | No | Defaults to COGNITO_REGION |
| `AVP_NAMESPACE` | No | Defaults to "Portal" |
| `AVP_SUPER_ADMIN_GROUP` | No | Defaults to "global:global:admin" |

---

## Schema Definitions Explained

### Entity Types

| Entity | What it represents | `memberOfTypes` | Attributes |
|--------|-------------------|-----------------|------------|
| `User` | A person (from Cognito `sub`) | `[Group]` — user can be in groups | `sub`, `email` |
| `Group` | A Cognito group (e.g. `ops:store:write`) | `[Group]` — groups can nest | `module`, `resource`, `access` |
| `Resource` | An API resource being protected | `[]` — no nesting | `module`, `resourceType` |

### Actions & Hierarchy

| Action | `memberOf` (inherits from) | What it represents |
|--------|---------------------------|-------------------|
| `Read` | `[]` (base) | View/list data |
| `Write` | `[Read]` | Create/update data (includes Read) |
| `Publish` | `[Read]` | Publish content (includes Read) |
| `Export` | `[Read]` | Export data (includes Read) |
| `Approve` | `[Read]` | Approve workflows (includes Read) |
| `Admin` | `[Write, Publish, Export, Approve]` | Full control (includes everything) |

**Hierarchy visualization:**
```
Admin ──┬── Write ──── Read
        ├── Publish ── Read
        ├── Export ─── Read
        └── Approve ── Read
```

### Policy Definitions Explained

Each policy follows a pattern based on the group name:

| Group Pattern | Policy Generated | Scope |
|---------------|-----------------|-------|
| `global:global:admin` | `permit(principal, action, resource)` | Everything, no conditions |
| `module:global:admin` | `permit(...) when { resource.module == "module" }` + `forbid(...) unless { resource.module == "module" }` | All actions within module |
| `module:resource:write` | `permit(... action in [Write] ...) when { module && resourceType }` | Write+Read on specific resource |
| `module:resource:read` | `permit(... action in [Read] ...) when { module && resourceType }` | Read only on specific resource |
| `module:resource:publish` | `permit(... action in [Publish] ...) when { module && resourceType }` | Publish+Read on specific resource |
