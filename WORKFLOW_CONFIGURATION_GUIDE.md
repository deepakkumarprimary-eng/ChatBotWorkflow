# Chatbot Workflow Builder — State Configuration Guide

This guide explains how to configure each state type so that your users can build chatbot workflows and the chatbot can execute them end-to-end.

---

## How It Works (Big Picture)

A workflow is a **directed graph of states connected by transitions**. When executed:

1. The engine finds the **start state** (the state with no incoming transitions)
2. It processes the start state using its type-specific processor
3. The processor returns an outcome + transition condition
4. The engine resolves the next state via the matching outgoing transition
5. Repeat until an **End** state is reached, execution fails, or it times out

**Context Variables** carry data between states — they're initialized with defaults at execution start and can be read/written by any state.

---

## Context Variables

Define workflow-level variables in the **Property Panel** (shown when no state is selected).

| Field | Rules | Example |
|-------|-------|---------|
| Name | `^[a-zA-Z0-9_]{1,64}$` | `user_name`, `order_id` |
| Default Value | Any JSON value (string, number, boolean, null) | `""`, `0`, `null` |

States can reference these using `{{variableName}}` syntax in templates and URLs.

---

## State Types

### 1. API Call

Makes an HTTP request to an external service and maps the response into context variables.

**Frontend Configuration (Property Panel):**

| Field | Description | Validation |
|-------|-------------|------------|
| Method | HTTP method | GET, POST, PUT, PATCH, DELETE |
| URL | Target endpoint | Supports `{{variableName}}` interpolation |
| Headers | Key-value pairs | Supports `{{variableName}}` interpolation in values |
| Body | Request body (for POST/PUT/PATCH) | Supports `{{variableName}}` interpolation |
| Response Mapping | Maps JSON response fields to context variables | `variableName → jsonField` |
| Timeout | Seconds before timeout (1–120, default 30) | Integer |

**Backend Processing:**
- Interpolates all `{{variableName}}` placeholders in URL, body, and header values
- Makes the HTTP request with the configured timeout
- On **success (2xx)**: outcome = `SUCCEEDED`, stores `_apiResponse_statusCode` and `_apiResponse_body` in context, applies response mapping
- On **HTTP error (4xx/5xx)**: outcome = `FAILED`, transition condition = `"error"`
- On **timeout/network error**: outcome = `FAILED`, transition condition = `"timeout"`

**Transitions from API Call:**
| Condition | When |
|-----------|------|
| *(none/default)* | Request succeeded (2xx) |
| `error` | Request got a non-2xx response |
| `timeout` | Request timed out or network error |

**Retry Policy (optional):**
- Max Retries: 0–10
- Backoff Interval: 1–300 seconds

**Example:**
```
Method: POST
URL: https://api.example.com/users/{{userId}}
Headers: { "Authorization": "Bearer {{authToken}}" }
Body: {"query": "{{userQuery}}"}
Response Mapping: { "answer": "response_text", "score": "confidence" }
Timeout: 15
```

---

### 2. Condition

Evaluates a boolean expression and routes to different states based on `true`/`false`.

**Frontend Configuration:**

| Field | Description | Example |
|-------|-------------|---------|
| Expression | Boolean expression | `age > 18 AND status == "active"` |

**Supported Operators:**
- Comparison: `==`, `!=`, `<`, `>`, `<=`, `>=`
- Logical: `AND`, `OR`, `NOT`
- Parentheses for grouping: `(age > 18) AND (status == "active")`
- Literals: numbers, `"strings"`, `true`, `false`, `null`
- Identifiers resolve to context variable values

**Backend Processing:**
- Parses the expression using a recursive descent parser
- Resolves identifier tokens as context variable names
- Undefined variables evaluate to `null`
- Returns transition condition `"true"` or `"false"`

**Transitions from Condition:**
| Condition | When |
|-----------|------|
| `true` | Expression evaluated to truthy |
| `false` | Expression evaluated to falsy |
| `error` | Expression is invalid or empty |

**Example workflow pattern:**
```
[API Call] → [Condition: score > 0.8] 
               ├─ true → [Response: "High confidence answer"]
               └─ false → [Response: "I'm not sure, let me check"]
```

---

### 3. Response

Sends a message to the user by interpolating context variables into a template.

**Frontend Configuration:**

| Field | Description | Example |
|-------|-------------|---------|
| Message Template | Text with `{{var}}` placeholders | `"Hello {{user_name}}, your order {{order_id}} is confirmed."` |

**Backend Processing:**
- Replaces all `{{variableName}}` with context variable values
- Undefined variables are replaced with the string `"null"`
- Stores the interpolated message as `_responseMessage` in context
- Always returns `SUCCEEDED`

**Transitions from Response:**
| Condition | When |
|-----------|------|
| *(none/default)* | Always — moves to next state |

**Example:**
```
Message Template: "Hi {{user_name}}! The answer to your question is: {{answer}}"
```

---

### 4. Input

Pauses execution and waits for user input. The user's response is stored in a context variable.

**Frontend Configuration:**

| Field | Description | Validation |
|-------|-------------|------------|
| Prompt | Message shown to the user | Required, non-empty |
| Variable Name | Context variable to store input | Must match `^[a-zA-Z0-9_]{1,64}$` |
| Timeout | Seconds to wait (default 300) | Positive integer |

**Backend Processing:**
- Validates prompt and variableName are present
- Stores `_inputPrompt`, `_inputVariableName`, `_inputTimeout` in output
- **Pauses execution** — engine saves state and waits
- When resumed (user provides input), the input value is stored in the named context variable

**Transitions from Input:**
| Condition | When |
|-----------|------|
| *(none/default)* | User provided input and execution resumed |
| `timeout` | User didn't respond within timeout |

**Example chatbot flow:**
```
[Response: "What is your name?"] → [Input: prompt="Please type your name", variableName="user_name"]
    → [Response: "Nice to meet you, {{user_name}}!"]
```

---

### 5. Wait

Pauses execution for a specified duration before continuing.

**Frontend Configuration:**

| Field | Description | Validation |
|-------|-------------|------------|
| Duration | Wait time in seconds | 1–86,400 (1 sec to 24 hours) |

**Backend Processing:**
- Validates duration is within range
- Stores `_waitDuration` and `_waitStartTime` in output
- **Pauses execution** — external scheduler resumes after duration

**Transitions from Wait:**
| Condition | When |
|-----------|------|
| *(none/default)* | Wait period elapsed |

**Use case:** Adding a delay between API calls (rate limiting), giving the user time to read a long response, or scheduling follow-up messages.

---

### 6. Parallel

Executes 2–10 branches concurrently and merges their results.

**Frontend Configuration:**

| Field | Description | Validation |
|-------|-------------|------------|
| Branches | Named branch definitions (2–10) | Each branch has a name and list of state IDs |

**Backend Processing:**
- Validates branch count (2–10)
- Executes all branches concurrently using a thread pool
- Each branch gets its own copy of context variables
- Merges output variables in branch-definition order (later branches overwrite earlier ones)
- If any branch fails, remaining branches are **cancelled**

**Transitions from Parallel:**
| Condition | When |
|-----------|------|
| *(none/default)* | All branches completed successfully |
| `error` | Any branch failed |

**Example:**
```
[Parallel]
  ├─ Branch 1: "Fetch user profile" (API Call)
  └─ Branch 2: "Fetch order history" (API Call)
→ [Response: "{{profile_name}} has {{order_count}} orders"]
```

---

### 7. End

Terminates the workflow execution. Every workflow must have at least one End state.

**Frontend Configuration:** None needed.

**Backend Processing:**
- Returns `SUCCEEDED` with no output
- Engine marks execution as `COMPLETED`

**Transitions from End:** None — execution terminates here.

---

## Transitions

Transitions connect states and control the flow. They are the edges/arrows on the canvas.

| Property | Description |
|----------|-------------|
| Source | The state this transition leaves from |
| Target | The state this transition goes to |
| Condition | When to follow this transition (optional) |

**Transition Conditions:**

| Value | Used By | Meaning |
|-------|---------|---------|
| *(null/none)* | All states | Default path — followed when no specific condition matches |
| `true` | Condition | Expression was truthy |
| `false` | Condition | Expression was falsy |
| `error` | API Call, Condition, Parallel | State encountered an error |
| `timeout` | API Call, Input | Request/input timed out |

**Resolution logic:** The engine first looks for a transition matching the exact condition from the processor. If not found, it falls back to the default (null-condition) transition. If no transition exists, execution completes.

---

## Building a Complete Chatbot Workflow (Step by Step)

### Example: Order Status Bot

1. **Define Context Variables:**
   - `user_input` (default: `""`) — what the user types
   - `order_id` (default: `""`) — extracted order ID
   - `order_status` (default: `""`) — status from API

2. **Add States:**
   - **Response** → "Welcome! Please provide your order ID."
   - **Input** → prompt: "Enter order ID", variableName: `user_input`
   - **API Call** → GET `https://api.mystore.com/orders/{{user_input}}`
     - Response Mapping: `{ "order_status": "status" }`
   - **Condition** → `order_status == "shipped"`
   - **Response (true)** → "Your order {{user_input}} has been shipped!"
   - **Response (false)** → "Your order {{user_input}} is still being processed (status: {{order_status}})."
   - **End**

3. **Connect with Transitions:**
   ```
   Response → Input → API Call → Condition
                                    ├─ true → Response (shipped) → End
                                    └─ false → Response (processing) → End
   ```
   Add an `error` transition from API Call to a "Sorry, couldn't find that order" Response.

4. **Save and Execute** — Click the Execute button in the toolbar.

---

## Execution Lifecycle

| Status | Meaning |
|--------|---------|
| `running` | Engine is actively processing states |
| `paused` | Waiting for user input or wait duration to elapse |
| `completed` | Reached an End state successfully |
| `failed` | A state failed with no error transition available |

**API Endpoints:**

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/workflows/{id}/execute` | Start execution (returns 202 + executionId) |
| GET | `/api/executions` | List all executions (paginated) |
| GET | `/api/executions/{id}` | Get execution details + history |

---

## Retry Policy

Available on all states except End. When enabled:

- **Max Retries** (0–10): How many times to retry on failure
- **Backoff Interval** (1–300s): Wait time between retries

The retry manager uses the backoff interval as a base delay. If a state fails and retries are configured, the engine retries before following the error transition.

---

## Tips

- The **start state** is automatically determined — it's the state with no incoming transitions
- Use **Response** before **Input** to prompt the user, then **Input** to capture their answer
- Chain **API Call → Condition** to branch based on API response values
- Use **Response Mapping** on API Call to extract specific JSON fields into named variables
- Always provide an `error` transition from API Call states for graceful failure handling
- The `{{variableName}}` syntax works in: API Call URL, headers, body; Response template
- End states are required — execution won't complete without reaching one
