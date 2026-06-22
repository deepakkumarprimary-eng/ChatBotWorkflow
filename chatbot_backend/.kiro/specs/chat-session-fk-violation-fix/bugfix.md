# Bugfix Requirements Document

## Introduction

When a WebSocket client subscribes to `/app/chat.init`, the `ChatWebSocketHandler.onChatInit()` method creates a `ChatSession` entity with `workflowId = 0L` and immediately persists it to the database. The `chat_session` table has a foreign key constraint (`chat_session_workflow_id_fkey`) requiring `workflow_id` to reference an existing row in the `workflow` table. Since no workflow with `id = 0` exists, the insert fails with a `PSQLException` (FK violation). This prevents any client from initializing a chat session.

The fix defers `ChatSession` persistence until `chat.start` is called (when the actual `workflowId` is known), generating only an in-memory `sessionId` at init time.

## Bug Analysis

### Current Behavior (Defect)

1.1 WHEN a client subscribes to `/app/chat.init` THEN the system persists a `ChatSession` with `workflowId = 0L`, causing a foreign key constraint violation (`PSQLException: Key (workflow_id)=(0) is not present in table "workflow"`)

1.2 WHEN the FK constraint is enforced on the `chat_session` table THEN the system crashes on every `/app/chat.init` subscription because the dummy `workflowId = 0` does not reference any existing workflow row

### Expected Behavior (Correct)

2.1 WHEN a client subscribes to `/app/chat.init` THEN the system SHALL generate a session ID in-memory and return it along with the available workflow list without persisting any `ChatSession` row to the database

2.2 WHEN a client sends `/app/chat.start` with a valid `sessionId` and `workflowId` THEN the system SHALL create and persist a new `ChatSession` entity with the provided `workflowId` (which references a valid workflow row) and proceed with workflow execution

### Unchanged Behavior (Regression Prevention)

3.1 WHEN a client subscribes to `/app/chat.init` THEN the system SHALL CONTINUE TO return a response containing a unique `sessionId` and the list of available workflows

3.2 WHEN a client sends `/app/chat.start` with a valid `sessionId` and `workflowId` THEN the system SHALL CONTINUE TO load the workflow, find the first node, and begin processing the workflow from that node

3.3 WHEN a client sends `/app/chat.message` with a valid `sessionId` and `message` THEN the system SHALL CONTINUE TO look up the existing `ChatSession` by `sessionId` and handle the user input normally

3.4 WHEN a client sends `/app/chat.start` with an invalid `workflowId` (no matching row in `workflow` table) THEN the system SHALL CONTINUE TO send an error message ("Workflow not found") without crashing
