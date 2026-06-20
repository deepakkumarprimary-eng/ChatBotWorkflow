# Requirements Document

## Introduction

This feature adds a new "workflow" node type to the chatbot workflow engine. A workflow node references another workflow by its ID. When the execution engine encounters this node, it loads the referenced workflow and begins executing it from its first node within the same active session. This enables workflow composition and reuse — allowing complex conversational flows to be broken into smaller, manageable, reusable workflows that can be called from any parent workflow.

## Glossary

- **Execution_Engine**: The WorkflowExecutionServiceImpl component responsible for processing nodes sequentially, managing session state, and routing between nodes
- **Workflow_Node**: A node of type "workflow" within a workflow's node list that references another workflow by its workflowId
- **Parent_Workflow**: The workflow currently being executed that contains a workflow node referencing a child workflow
- **Child_Workflow**: The workflow that is loaded and executed when a workflow node is encountered
- **Session_Context**: The JSONB map stored on the ChatSession entity containing all variable values accumulated during execution
- **Workflow_Node_Processor**: The NodeProcessor implementation responsible for handling workflow-type nodes
- **Workflow_Stack**: A data structure stored in session context that tracks the chain of parent workflows and their return positions for resumption after child workflow completion
- **Recursion_Depth**: The count of nested workflow invocations currently active in the execution stack
- **Navigation_History**: An append-only list stored in session context that records every node visited during the session, including which workflow it belongs to and when it was visited

## Requirements

### Requirement 1: Workflow Node Definition

**User Story:** As a workflow designer, I want to define a workflow node that references another workflow by ID, so that I can compose workflows by calling one from another.

#### Acceptance Criteria

1. THE Execution_Engine SHALL recognize nodes with type "workflow" as valid workflow nodes
2. WHEN a workflow node is encountered, THE Workflow_Node_Processor SHALL read the workflowId as a Long value from the node's config map
3. IF the config map is missing or does not contain a workflowId entry, THEN THE Workflow_Node_Processor SHALL return a NodeProcessingResult with Action CONTINUE and a ChatResponse indicating that the workflow reference is missing
4. IF the workflowId value cannot be parsed as a Long, THEN THE Workflow_Node_Processor SHALL return a NodeProcessingResult with Action CONTINUE and a ChatResponse indicating that the workflow identifier is invalid
5. IF no workflow record exists in the database for the given workflowId, THEN THE Workflow_Node_Processor SHALL return a NodeProcessingResult with Action CONTINUE and a ChatResponse indicating that the referenced workflow was not found
6. WHEN a workflow node contains a valid workflowId referencing an existing workflow, THE Workflow_Node_Processor SHALL return a NodeProcessingResult with Action CONTINUE to signal the Execution_Engine to proceed with the referenced workflow

### Requirement 2: Child Workflow Loading and Execution

**User Story:** As a chatbot user, I want the engine to seamlessly execute a referenced workflow when a workflow node is reached, so that the conversation continues naturally without interruption.

#### Acceptance Criteria

1. WHEN a node of type "workflow" is processed, THE Workflow_Node_Processor SHALL read the workflowId from the node's config object and load the referenced workflow from the database
2. IF the workflowId is missing or null in the workflow node's config, THEN THE Workflow_Node_Processor SHALL return an error response indicating that the workflow node has no valid workflowId configured
3. IF the referenced workflow does not exist in the database, THEN THE Workflow_Node_Processor SHALL return an error response indicating that the referenced workflow was not found
4. IF the referenced workflow's transitions list is empty or missing, THEN THE Workflow_Node_Processor SHALL return an error response indicating that the child workflow has no starting node
5. WHEN the child workflow is loaded successfully, THE Execution_Engine SHALL begin executing from the child workflow's first node using the current session and its existing context, preserving all previously stored variables
6. WHEN the child workflow execution reaches its last node with no further transitions, THE Execution_Engine SHALL resume the parent workflow from the node following the workflow node that triggered the child execution

### Requirement 3: Session Context Preservation

**User Story:** As a chatbot user, I want session context to be shared between parent and child workflows, so that variables collected in one workflow are available in the other.

#### Acceptance Criteria

1. WHEN a child workflow begins execution, THE Execution_Engine SHALL pass the existing Session_Context to the child workflow without clearing or resetting any values
2. WHILE a child workflow is executing, THE Execution_Engine SHALL store any new variables collected by the child workflow into the same Session_Context
3. WHEN a child workflow completes, THE Execution_Engine SHALL retain all variables added by the child workflow in the Session_Context for use by the parent workflow
4. WHEN a child workflow begins execution, THE Execution_Engine SHALL clear any transient engine variables (keys prefixed with underscore such as _targetNodeId, _inputVariableName, _displayVariable, _buttonOptions) from the Session_Context before processing the child workflow's first node
5. IF a child workflow stores a variable with the same key as an existing variable in the Session_Context, THEN THE Execution_Engine SHALL overwrite the existing value with the child workflow's value

### Requirement 4: Return to Parent Workflow

**User Story:** As a chatbot user, I want the conversation to resume in the parent workflow after a child workflow finishes, so that the full conversational flow completes as designed.

#### Acceptance Criteria

1. WHEN a workflow node is processed and no Workflow_Stack exists in the session context, THE Execution_Engine SHALL initialize the Workflow_Stack as an empty list in the session context before pushing any entry
2. WHEN a workflow node is processed, THE Execution_Engine SHALL push an entry containing the current parent workflow ID and the workflow node ID onto the Workflow_Stack in the session context
3. WHEN a child workflow's currently processing node has no outgoing transitions in the workflow definition, THE Execution_Engine SHALL pop the top entry from the Workflow_Stack
4. WHEN the Workflow_Stack entry is popped, THE Execution_Engine SHALL restore the session's workflowId to the parent workflow ID from the popped entry, load the parent workflow, and resolve the next node after the workflow node identified by the popped entry's workflow node ID
5. IF the resolved next node in the parent workflow has no outgoing transitions, THEN THE Execution_Engine SHALL repeat the pop-and-resume process for the next entry in the Workflow_Stack
6. WHEN the Workflow_Stack is empty and the current workflow's processing node has no outgoing transitions, THE Execution_Engine SHALL set the session status to "completed"
7. IF the parent workflow cannot be loaded during return, THEN THE Execution_Engine SHALL send an error response indicating that the parent workflow is unavailable and preserve the current session context without modification

### Requirement 5: Recursion Protection

**User Story:** As a system operator, I want the engine to prevent infinite recursion between workflows, so that the system remains stable and responsive.

#### Acceptance Criteria

1. THE Execution_Engine SHALL enforce a maximum Recursion_Depth of 10 nested workflow invocations
2. IF a workflow node is encountered and the current Recursion_Depth equals or exceeds 10, THEN THE Execution_Engine SHALL reject the workflow node execution before loading the child workflow and send an error response indicating maximum nesting depth has been exceeded
3. THE Execution_Engine SHALL determine the current Recursion_Depth by counting the entries in the Workflow_Stack before pushing the new entry for the current workflow node
4. IF the Execution_Engine rejects a workflow node due to recursion depth, THEN THE Execution_Engine SHALL halt execution of the current session without marking it as completed and without modifying the Workflow_Stack

### Requirement 6: Workflow Node Processor Registration

**User Story:** As a developer, I want the workflow node processor to follow the existing NodeProcessor pattern, so that it integrates cleanly with the current architecture.

#### Acceptance Criteria

1. THE Workflow_Node_Processor SHALL implement the NodeProcessor interface with canHandle and process methods
2. WHEN the node map contains a "type" value equal to "workflow", THE Workflow_Node_Processor SHALL return true from canHandle
3. WHEN the node map does not contain a "type" value equal to "workflow", THE Workflow_Node_Processor SHALL return false from canHandle
4. THE Workflow_Node_Processor SHALL be annotated as a Spring @Component with @Order(4) so that it is auto-discovered after the existing processors (InputNodeProcessor @Order(1), MessageNodeProcessor @Order(2), ApiNodeProcessor @Order(3))

### Requirement 7: Child Workflow Pause and Resume

**User Story:** As a chatbot user, I want input nodes within a child workflow to pause and resume correctly, so that the conversation works the same regardless of nesting.

#### Acceptance Criteria

1. WHEN an input node within a child workflow pauses execution, THE Execution_Engine SHALL save the session state with the child workflow's currentNodeId, currentNodeType, and the child workflow's workflowId on the session entity
2. WHEN the user replies after a pause in a child workflow, THE Execution_Engine SHALL load the workflow using the session's current workflowId, which points to the child workflow, for node resolution and transition lookups
3. WHILE executing within a child workflow, THE Execution_Engine SHALL use the child workflow's workflowId stored on the session for all resolveNextNode and findNodeById operations
4. WHEN an API node within a child workflow pauses execution for interactive selection or button options, THE Execution_Engine SHALL save the session state identically to input node pauses, preserving the child workflow's workflowId
5. IF the workflow referenced by the session's workflowId cannot be loaded during resume, THEN THE Execution_Engine SHALL send an error response indicating the workflow is unavailable

### Requirement 8: Navigation History Tracking

**User Story:** As a system operator, I want every node visit recorded with its workflow context and timestamp, so that I can debug conversation flows and enable back-button navigation in the future.

#### Acceptance Criteria

1. THE Execution_Engine SHALL maintain a Navigation_History list stored under the key `_navigationHistory` in the Session_Context
2. WHEN the Execution_Engine begins processing any node (message, input, api, or workflow), THE Execution_Engine SHALL append an entry to the Navigation_History containing the current workflowId, the node's id, and the current timestamp in ISO-8601 format before invoking the node's processor logic
3. THE Execution_Engine SHALL record the navigation entry before any state change (Workflow_Stack push, session pause, or workflow switch) so that paused or failed nodes are still captured in the history
4. THE Execution_Engine SHALL NOT remove or modify existing entries in the Navigation_History — it is append-only
5. WHEN the session status is set to "completed", THE Execution_Engine SHALL preserve the full Navigation_History in the Session_Context for post-session analysis
6. THE Navigation_History entry format SHALL be: `{"workflowId": <Long>, "nodeId": "<String>", "timestamp": "<ISO-8601 String>"}`
