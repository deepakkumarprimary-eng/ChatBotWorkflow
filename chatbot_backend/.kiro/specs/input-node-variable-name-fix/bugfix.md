# Bugfix Requirements Document

## Introduction

In `WorkflowExecutionServiceImpl.handleInputNodeResume()`, user input is always stored under the hardcoded key `"mobile_no"` in the session context (`context.put("mobile_no", message)`). This means every input node in a workflow overwrites the same context key regardless of what the node is actually collecting (email, order_id, name, etc.). Multiple input nodes in the same workflow cannot collect different fields because they all write to `"mobile_no"`.

The fix uses the same temporary-context-key pattern already established by API nodes (`_displayVariable`, `_buttonOptions`): `InputNodeProcessor.process()` stores the node's configured variable name into `"_inputVariableName"` before pausing, and `handleInputNodeResume()` reads that key to store the user's reply under the correct variable name.

## Bug Analysis

### Current Behavior (Defect)

1.1 WHEN an input node with `config.variableName` set to any value (e.g. "email", "order_id") pauses and the user provides a reply THEN the system stores the reply under the hardcoded key `"mobile_no"` instead of the configured variable name

1.2 WHEN a workflow contains multiple input nodes collecting different fields (e.g. "mobile_no", "email", "order_id") and the user progresses through each node THEN the system overwrites the same `"mobile_no"` context key with every reply, losing previously collected values

1.3 WHEN downstream nodes (message, API) reference a placeholder like `{{email}}` or `{{order_id}}` after an input node collected that value THEN the system cannot resolve the placeholder because the value was stored under `"mobile_no"` instead of the expected key

### Expected Behavior (Correct)

2.1 WHEN an input node with `config.variableName` set to a non-empty value pauses and the user provides a reply THEN the system SHALL store the reply under the key matching `config.variableName` (e.g. context key `"email"` for `variableName: "email"`)

2.2 WHEN an input node has no `config.variableName` or it is empty THEN the system SHALL fall back to using the node's `id` as the context key for storing the user's reply

2.3 WHEN `InputNodeProcessor.process()` is invoked for an input node THEN the system SHALL store the resolved variable name (from `config.variableName` or node id fallback) into the session context under the temporary key `"_inputVariableName"` before returning PAUSE

2.4 WHEN `handleInputNodeResume()` processes the user's reply THEN the system SHALL read the `"_inputVariableName"` key from context, use its value as the storage key for the user's message, and remove `"_inputVariableName"` from context afterward

2.5 WHEN a workflow contains multiple input nodes collecting different fields THEN the system SHALL store each reply under its own distinct context key, preserving all previously collected values

### Unchanged Behavior (Regression Prevention)

3.1 WHEN an input node with `config.variableName` set to `"mobile_no"` pauses and the user replies THEN the system SHALL CONTINUE TO store the reply under the key `"mobile_no"` (same as current behavior for this specific case)

3.2 WHEN an input node pauses execution THEN the system SHALL CONTINUE TO set `currentNodeType` to `"input"`, set `currentNodeId` to the node's id, and return a PAUSE action with the node's prompt message

3.3 WHEN the user replies to a paused input node THEN the system SHALL CONTINUE TO load the workflow, resolve the next node, persist session state, and continue processing from the next node

3.4 WHEN API nodes use `_displayVariable` or `_buttonOptions` temporary context keys THEN the system SHALL CONTINUE TO handle those keys with existing logic unchanged
