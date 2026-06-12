# Product Overview

The Chatbot Workflow Builder is a visual drag-and-drop editor for designing and executing chatbot conversation workflows modeled as state machines.

## Core Concepts

- **Workflows** are directed graphs of states connected by transitions
- **States** represent actions: API Call, Condition, Response, Input, Wait, Parallel, End
- **Transitions** connect states with optional conditions (true/false/error/timeout/fallback)
- **Context Variables** carry data between states during execution
- **Executions** run a workflow instance in real-time with history tracking

## Key Capabilities

- Visual canvas editor for building workflows (drag, drop, connect)
- Workflow CRUD with automatic versioning (each save creates a new version)
- Structural validation before execution
- Asynchronous workflow execution with state-by-state monitoring
- Retry policies with configurable backoff on API Call states
- Workflow export/import as JSON files
- Undo/redo for canvas operations
