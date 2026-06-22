# Bugfix Requirements Document

## Introduction

The `ApiNodeProcessor.canHandle()` method uses an inconsistent node type check compared to all other node processors. It checks for `node.type == "api"` directly, while `MessageNodeProcessor`, `InputNodeProcessor`, and `WorkflowNodeProcessor` all check for `node.type == "state"` and differentiate via `config.nodeType`. This inconsistency forces API nodes to use a different top-level type in workflow JSON, breaks architectural uniformity, and requires the frontend to handle two different discrimination strategies.

## Bug Analysis

### Current Behavior (Defect)

1.1 WHEN a node has `type: "api"` in its JSON representation THEN the system routes it to `ApiNodeProcessor` based solely on the top-level `type` field, ignoring `config.nodeType`

1.2 WHEN a node has `type: "state"` and `config.nodeType: "api"` THEN the system does NOT route it to `ApiNodeProcessor`, because `canHandle()` only checks for `type == "api"`

1.3 WHEN the frontend needs to determine if a node is an API node THEN the system requires checking `node.type == "api"` (a different field than used for all other node types which use `node.config.nodeType`)

1.4 WHEN `ApiNodeProcessor.canHandle()` receives a node with `type: "state"` and `config` is null THEN the system does not handle the null config scenario for the API processor path (NPE risk in tests)

### Expected Behavior (Correct)

2.1 WHEN a node has `type: "state"` and `config.nodeType: "api"` THEN the system SHALL route it to `ApiNodeProcessor`

2.2 WHEN a node has `type: "api"` (legacy format) THEN the system SHALL NOT route it to `ApiNodeProcessor` (or alternatively, handle migration gracefully)

2.3 WHEN the frontend needs to determine node type THEN the system SHALL use a unified discrimination strategy where ALL nodes have `type: "state"` and are differentiated by `config.nodeType`

2.4 WHEN `ApiNodeProcessor.canHandle()` receives a node with `type: "state"` but `config` is null THEN the system SHALL return false without throwing an exception

### Unchanged Behavior (Regression Prevention)

3.1 WHEN a node has `type: "state"` and `config.nodeType: "input"` THEN the system SHALL CONTINUE TO route it to `InputNodeProcessor`

3.2 WHEN a node has `type: "state"` and `config` is null or `config.nodeType` is absent THEN the system SHALL CONTINUE TO route it to `MessageNodeProcessor`

3.3 WHEN a node has `type: "state"` and `config.nodeType: "workflow"` THEN the system SHALL CONTINUE TO route it to `WorkflowNodeProcessor`

3.4 WHEN `ApiNodeProcessor.process()` receives a valid API node (with correct type and config) THEN the system SHALL CONTINUE TO execute the HTTP call, extract responses, and handle routing logic identically to current behavior

3.5 WHEN workflow JSON contains nodes of type message, input, or workflow THEN the system SHALL CONTINUE TO process them without any changes to their JSON structure or behavior
