# Security Specification (Phase 0: Payload-First Security TDD)

## 1. Data Invariants
1. **Authentication & Multi-Tenancy Isolation**: Every operation on `/users/{userId}/tasks/{taskId}` and `/users/{userId}/workspaces/{workspaceId}` requires an authenticated user (`request.auth != null`) whose UID strictly matches both the path parameter `userId` and the document's `userId` field (`request.auth.uid == userId && incoming().userId == request.auth.uid`).
2. **Path Variable Hardening**: Document IDs (`userId`, `taskId`, `workspaceId`) on single-document operations (`get`, `create`, `update`, `delete`) must match `^[a-zA-Z0-9_\-]+$` and be at most 128 characters.
3. **Strict Key Allowlisting & Schema Validation**:
   - `Task`: Required keys `['title', 'notes', 'completed', 'priority', 'workspaceName', 'dueDateMillis', 'subtasks', 'tags', 'pinned', 'deviceName', 'userId', 'createdAt', 'updatedAt']`.
   - `Workspace`: Required keys `['name', 'colorHex', 'iconName', 'userId', 'createdAt', 'updatedAt']`.
4. **Temporal & Identity Immutability**:
   - On `create` and `update`, timestamp fields (`createdAt`, `updatedAt`) must be server timestamps (`is timestamp && <= request.time`).
   - On `update`, `userId` and `createdAt` must remain strictly equal to `existing().userId` and `existing().createdAt`.
5. **Bounded Arrays & Strings**:
   - `title`: 1..200 chars; `notes`: 0..2000 chars; `priority`: in `['LOW', 'MEDIUM', 'HIGH', 'URGENT']`; `workspaceName`: 1..50 chars; `deviceName`: 1..100 chars.
   - `subtasks`: list of max 20 strings (first element checked `is string && size() <= 200`).
   - `tags`: list of max 10 strings (first element checked `is string && size() <= 40`).

## 2. The "Dirty Dozen" Payloads
1. **Unauthenticated Read/Write**: `auth = null` attempting `get` or `list` on `/users/alice_123/tasks`.
2. **Cross-User Read (IDOR)**: `auth.uid = "bob_456"` attempting `get` or `list` on `/users/alice_123/tasks`.
3. **Identity Spoofing on Create**: `auth.uid = "alice_123"` creating `/users/alice_123/tasks/t1` with `{"userId": "bob_456"}`.
4. **Shadow Update (Ghost Field Injection)**: `auth.uid = "alice_123"` updating task with extra field `{"isVerified": true}`.
5. **Immutable Field Mutation (`createdAt`)**: Updating `createdAt` to a newer timestamp on an existing task.
6. **Immutable Field Mutation (`userId`)**: Updating `userId` to transfer ownership to another UID.
7. **String Size Overflow (Denial of Wallet)**: Creating a task with a 5,000-character `title` or `notes`.
8. **Enum Violation**: Setting `priority` to `"CRITICAL"` or `"low"` instead of `['LOW', 'MEDIUM', 'HIGH', 'URGENT']`.
9. **Unbounded Array Attack**: Passing 25 items in `subtasks` or 15 items in `tags`.
10. **Array Element Type Poisoning**: Passing `[12345]` (non-string) in `subtasks` or `tags`.
11. **Future Timestamp Spoofing**: Passing a client timestamp in the future (`createdAt > request.time`).
12. **Path ID Poisoning**: Creating a document with invalid characters or >128 chars in `{taskId}`.
