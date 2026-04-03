# Upstream Paths to Watch

These are the directories in the `upstream/` submodule that matter for mobile parity.
Use these paths when generating focused diffs between tags.

| Path | What It Contains | Why It Matters |
|------|-----------------|----------------|
| `api/server/routes/` | Express route definitions (REST endpoints) | Defines the API contract the mobile app calls |
| `api/server/controllers/` | Request handlers and business logic | Shows request/response shapes and validation |
| `packages/data-provider/src/` | Shared types, schemas, API client, config | Canonical type definitions; Mobile models must match |
| `packages/data-provider/src/types/` | TypeScript type subdirectory (queries, mutations, agents, etc.) | React Query hook types that define API shapes |
| `packages/data-schemas/src/` | Mongoose schemas and DB models | Database schema changes that affect API responses |
| `client/src/components/` | React UI components by feature area | Reference for Android UI feature parity |
| `client/src/hooks/` | Custom React hooks (data fetching, state) | Shows how the web app consumes APIs — informs ViewModel design |
| `client/src/store/` | Recoil/Jotai state atoms | Global state patterns — informs Android state management |
| `client/src/Providers/` | React Context providers | Feature flags, config-driven UI — informs Android feature gating |

## Diff Command Template

```bash
cd upstream && git diff {old_tag}..{new_tag} --stat -- \
  api/server/routes/ \
  api/server/controllers/ \
  packages/data-provider/src/ \
  packages/data-schemas/src/ \
  client/src/components/ \
  client/src/hooks/ \
  client/src/store/
```

For detailed diffs, run each path separately to keep output manageable.
