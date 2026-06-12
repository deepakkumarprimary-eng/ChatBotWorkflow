# Requirements Document

## Introduction

This feature modernizes the Chatbot Workflow Builder frontend by migrating from the deprecated `reactflow` (v11) package to its official successor `@xyflow/react`, replacing pervasive inline styles with a structured CSS module system for a modern visual design, and aligning all React code with current React 18 best practices.

## Glossary

- **Canvas_Library**: The `@xyflow/react` package (v12+), the official successor to the `reactflow` package, providing the visual node graph editor
- **CSS_Module**: A CSS file scoped to a single component using the `.module.css` naming convention, preventing style collisions
- **Design_Token**: A named CSS custom property (variable) representing a reusable design value such as color, spacing, radius, or shadow
- **Node_Component**: A custom React component rendered inside a canvas node, representing a workflow state
- **Toolbar**: The header bar containing workflow action buttons (New, Open, Save, Export, Import, Execute, Executions)
- **Palette**: The left sidebar listing draggable state types
- **Property_Panel**: The right sidebar for editing a selected state's configuration
- **Theme_System**: The set of CSS custom properties defined at the `:root` level that control the application's visual appearance

## Requirements

### Requirement 1: Migrate Canvas Library from reactflow to @xyflow/react

**User Story:** As a developer, I want to migrate from the deprecated `reactflow` package to `@xyflow/react`, so that the project uses the actively maintained library and can leverage its new features and fixes.

#### Acceptance Criteria

1. WHEN the application is built, THE Canvas_Library SHALL resolve all imports from `@xyflow/react` with zero imports from the `reactflow` package
2. THE Canvas_Library SHALL render the workflow canvas with identical visual output and interaction behavior as the previous `reactflow` implementation
3. WHEN a user drags a node, THE Canvas_Library SHALL update the node position using the `@xyflow/react` node change API
4. WHEN a user draws a connection between nodes, THE Canvas_Library SHALL create an edge using the `@xyflow/react` connection API
5. THE Canvas_Library SHALL register custom Node_Component types using the `@xyflow/react` nodeTypes mechanism
6. WHEN the canvas viewport changes, THE Canvas_Library SHALL report zoom and pan values through the `@xyflow/react` viewport API
7. THE Canvas_Library SHALL use the `@xyflow/react` hook `useReactFlow` for programmatic viewport control in ZoomControls
8. WHEN a palette item is dropped on the canvas, THE Canvas_Library SHALL convert screen coordinates to flow coordinates using the `@xyflow/react` `screenToFlowPosition` method
9. THE Canvas_Library SHALL import and render Background, MiniMap, and Controls from `@xyflow/react`
10. THE Canvas_Library SHALL import the base stylesheet from `@xyflow/react/dist/style.css`

### Requirement 2: Modernize Component Styling with CSS Modules and Notion-Inspired Design Tokens

**User Story:** As a developer, I want to replace inline styles with CSS modules and design tokens reflecting a clean Notion-inspired aesthetic, so that the codebase is maintainable, consistent, and easy to theme.

#### Acceptance Criteria

1. THE Theme_System SHALL define CSS custom properties for colors, spacing, border-radius, and typography at the `:root` level in a global stylesheet, using a light, flat visual language with no glassmorphism, gradients, or glow effects
2. WHEN a component is rendered, THE Theme_System SHALL apply styles from a co-located CSS_Module file (using the `.module.css` naming convention) rather than inline style objects, for every component under `src/components/`
3. THE Theme_System SHALL provide a muted color palette with neutral scale values based on light grays (background: #ffffff, surface: #ffffff, hover: #f1f1ef, border: rgba(0,0,0,0.06), text-primary: #37352f, text-secondary: #787774) and minimal accent colors (primary-base: #2383e2, success: #0f7b0f, warning: #d9730d, error: #e03e3e)
4. THE Theme_System SHALL provide spacing tokens following a 4px base scale (4, 8, 12, 16, 20, 24, 32, 40, 48, 64)
5. THE Theme_System SHALL provide border-radius tokens for small (3px), medium (6px), large (8px), and full (9999px) values
6. THE Theme_System SHALL NOT define any box-shadow elevation tokens, glow tokens, or backdrop-filter values; all panels and surfaces SHALL share space via subtle borders only
7. THE CSS_Module SHALL reference design token CSS custom properties rather than hardcoded hex, RGB, or HSL values for all color declarations
8. THE Toolbar SHALL apply styles exclusively through a CSS_Module with no inline style attributes on HTML elements
9. THE Palette SHALL apply styles exclusively through a CSS_Module with no inline style attributes on HTML elements
10. THE Property_Panel SHALL apply styles exclusively through a CSS_Module with no inline style attributes on HTML elements
11. THE Theme_System SHALL provide typography tokens defining font-size values for at least 4 levels (small: 12px, base: 14px, medium: 16px, large: 20px) and font-weight values for regular (400), medium (500), and semibold (600) to establish hierarchy through typography rather than color or shadow
12. THE Theme_System SHALL define a background color of #ffffff or #fbfbfa for the application shell, with no dark backgrounds, no mesh gradients, and no translucent surface colors

### Requirement 3: Modernize Visual Design with Notion-Inspired Aesthetic

**User Story:** As a user, I want the application to have a clean, minimal Notion-like appearance, so that it feels calm, professional, and content-focused without visual clutter.

#### Acceptance Criteria

1. THE Toolbar SHALL render as a thin, flat bar with a 1px bottom border using rgba(0,0,0,0.06) and a solid white background with no blur, no gradients, and no glass effects
2. THE Toolbar SHALL use text-only or minimal icon buttons with a light gray background tint (#f1f1ef) on hover and a visible focus outline on keyboard focus, with no elevation changes or glow effects on interaction
3. THE Palette SHALL display state type items as a flat tree-style list with text labels and muted gray line-style icons, with a light background tint (#f1f1ef) on hover and no card styling, no shadows, and no elevation transitions
4. THE Palette SHALL use a collapsible section header with a chevron icon that rotates to indicate expanded or collapsed state
5. THE Node_Component SHALL render as a simple rounded card with border-radius using the design token for medium radius (6px), a solid white background, and a 1px solid border using rgba(0,0,0,0.06), with no gradients, no backdrop-filter, and no drop shadows
6. WHEN a Node_Component is selected, THE Node_Component SHALL display a 2px border using a light blue color (#2383e2) and a very light blue background tint (rgba(35,131,226,0.03)), with no animated outline ring and no glow effects
7. WHEN a Node_Component is hovered, THE Node_Component SHALL display a slightly darker border (rgba(0,0,0,0.12)) with a transition duration of 120ms and no shadow changes
8. THE Property_Panel SHALL render as a flat sidebar with a 1px left border using rgba(0,0,0,0.06), a solid white background, and use font-weight and font-size differences to create hierarchy between labels and values rather than cards, dividers, or shadows
9. THE Canvas_Library background SHALL display a dot grid pattern with dots spaced at regular intervals using a light neutral color (#e8e8e5) against a white or off-white (#fbfbfa) canvas background
10. THE application shell SHALL use #ffffff or #fbfbfa as the base background color, with all panels sharing the same white background and separated only by subtle 1px borders using rgba(0,0,0,0.06)

### Requirement 4: Modernize React Patterns and Code Quality

**User Story:** As a developer, I want the codebase to follow current React 18 best practices, so that it is performant, readable, and maintainable.

#### Acceptance Criteria

1. WHEN a component renders a list of items, THE component SHALL provide a stable, unique key derived from item identity rather than array index
2. WHEN a callback is passed to a child component, THE parent component SHALL wrap the callback in useCallback with an accurate dependency array
3. WHEN a value is derived from state or props through iteration over a collection or repeated computation across render cycles, THE component SHALL compute the value inside useMemo with an accurate dependency array
4. THE application SHALL not use class components anywhere in the frontend `src/` directory, excluding third-party type declaration files
5. WHEN a component accepts event handler props, THE component SHALL define the handler prop types using React's built-in event types
6. THE application SHALL pass TypeScript strict mode compilation with zero errors after migration
7. WHEN a component uses three or more useState calls where updating one state value requires reading or updating another, THE component SHALL use useReducer or extract state into a custom hook
8. THE application SHALL not use `any` type annotations in component props or hook return types
9. WHEN a useEffect subscribes to external resources or starts asynchronous operations, THE effect SHALL return a cleanup function that cancels subscriptions or pending operations

### Requirement 5: Maintain Functional Parity During Migration

**User Story:** As a user, I want all existing features to continue working identically after the modernization, so that no functionality is lost.

#### Acceptance Criteria

1. WHEN a user drags a state from the Palette to the canvas, THE Canvas_Library SHALL create a node at the drop position with the dropped state type and an initialized default configuration matching that state type's config interface
2. WHEN a user connects two nodes, THE Canvas_Library SHALL create a transition with the condition determined by the source handle identifier (true, false, error, timeout, or fallback), or no condition if the source node type has a single output
3. WHEN a user presses Ctrl+Z and the undo stack is non-empty, THE application SHALL reverse the most recent canvas operation and move it to the redo stack
4. WHEN a user presses Ctrl+Shift+Z and the redo stack is non-empty, THE application SHALL re-apply the most recently undone operation and move it to the undo stack
5. WHEN a user saves a workflow, THE application SHALL serialize the canvas state to a WorkflowDefinition and persist it via the backend API
6. IF the backend API returns an error during save or load, THEN THE application SHALL display an error message indicating the failure reason without losing the current canvas state
7. WHEN a user loads a workflow, THE application SHALL deserialize the API response and render all states and transitions on a fresh canvas
8. WHEN a user exports a workflow, THE application SHALL generate a downloadable JSON file containing the full WorkflowDefinition including metadata, states, transitions, and context variables
9. WHEN a user imports a JSON file, THE application SHALL validate the file size is at most 5 MB, parse the JSON, validate the workflow structure, and render the workflow on the canvas
10. IF a user imports a file that fails validation (exceeds 5 MB, contains invalid JSON, or has an invalid structure), THEN THE application SHALL display an error message indicating the failure reason without modifying the current canvas state
11. WHEN a user selects a node, THE Property_Panel SHALL display the configuration form corresponding to that node's state type
12. WHEN a user executes a workflow, THE application SHALL start execution via the API and display the execution monitor
13. WHEN a user deletes a state, THE application SHALL show a confirmation dialog displaying the state name and the count of affected transitions before removing the state and its transitions
14. THE Canvas_Library SHALL maintain a zoom range of 25% to 400% with snap-to-grid alignment at 16px intervals

### Requirement 6: Preserve Test Infrastructure Compatibility

**User Story:** As a developer, I want the testing infrastructure to remain fully functional after migration, so that property-based and unit tests continue to validate correctness.

#### Acceptance Criteria

1. WHEN `npm test` is executed from the `frontend/` directory, THE test runner SHALL exit with code 0 and report all test suites passing with no test file modifications required beyond changes to source modules that tests import from
2. WHEN a property-based test uses fast-check to generate canvas states, THE test SHALL continue to validate the same invariants (self-loop rejection, zoom clamping, state deletion with transition cleanup, undo reversibility, and canvas state preservation on failed operations) against the new Canvas_Library API with no reduction in the number of property checks per test (100 runs per property)
3. THE application SHALL maintain test file naming convention of `*.properties.test.ts` for property-based tests and `*.test.tsx` for component tests
4. WHEN the build command `npm run build` is executed from the `frontend/` directory, THE build SHALL complete with exit code 0, zero TypeScript errors, and zero TypeScript warnings
5. WHEN `npm test` is executed, THE test runner SHALL discover and execute all test files (property-based tests in `src/__tests__/` and component tests in `src/components/`) and report zero import resolution failures
6. IF a test file imports types or utilities from internal modules (e.g., `types/canvas.types`, `utils/canvasOperations`, `utils/validators`, `services/workflowExportImport`), THEN THE imported module SHALL export the same public type signatures and function signatures that the test depends on, ensuring no type errors in test compilation

### Requirement 7: Decompose App.tsx State into Focused Custom Hooks

**User Story:** As a developer, I want App.tsx state management decomposed into focused custom hooks, so that the root component is a thin composition shell and each concern is independently testable.

#### Acceptance Criteria

1. THE App component SHALL use a `useWorkflowPersistence` custom hook that encapsulates save, update, load, import, and export logic including the currentWorkflowId, currentWorkflowName, currentWorkflowDescription, saveDialogVisible, and listPanelVisible state
2. THE App component SHALL use a `useExecutionState` custom hook that encapsulates execution monitoring state (activeExecutionId, executionListVisible) and the execute action
3. WHEN the decomposition is complete, THE App component SHALL contain at most 5 direct useState or useReducer calls excluding those inside custom hooks
4. WHEN a custom hook is extracted, THE custom hook SHALL be independently unit-testable by importing and calling it with `renderHook` from @testing-library/react-hooks
5. THE `useWorkflowPersistence` hook SHALL expose the same API surface (same callbacks and state values) that App.tsx currently provides to child components

### Requirement 8: Accessibility for Interactive Controls

**User Story:** As a user who relies on assistive technology, I want all interactive controls to be properly labeled and keyboard-navigable, so that I can use the application without a mouse.

#### Acceptance Criteria

1. WHEN a button in the Toolbar contains only an icon with no visible text, THE button SHALL have an `aria-label` attribute describing its action
2. WHEN a button in the Toolbar has visible text, THE button SHALL use that text as its accessible name without a separate aria-label attribute
3. THE application SHALL make all interactive controls (buttons, inputs, selects) reachable via Tab key navigation in a logical order
4. THE Palette drag items SHALL have `role="listitem"` within a container with `role="list"` and an `aria-label` describing the palette purpose
5. THE Property_Panel form fields SHALL have associated `<label>` elements or `aria-label` attributes
6. WHEN a modal dialog (SaveWorkflowDialog, ConfirmDeleteDialog) is open, THE modal dialog SHALL trap focus within the dialog and return focus to the trigger element when closed
7. THE canvas area SHALL have `role="application"` and an `aria-label` describing it as the workflow editor canvas

### Requirement 9: Application-Level Error Boundary

**User Story:** As a user, I want the application to gracefully handle unexpected rendering errors, so that a crash in one component does not take down the entire application.

#### Acceptance Criteria

1. THE application SHALL wrap the canvas area in a React error boundary that catches rendering errors and displays a fallback UI with an error message and a "Reset" button
2. THE application SHALL wrap the entire App shell in a top-level error boundary that catches unhandled errors outside the canvas
3. WHEN an error boundary catches an error, THE error boundary SHALL log the error details to the console and display a user-friendly message without exposing stack traces
4. WHEN the user clicks the "Reset" button in the canvas error boundary, THE canvas SHALL re-mount with an empty state without reloading the page
5. THE top-level error boundary SHALL display a "Reload" button that reloads the page when triggered
6. THE error boundaries SHALL NOT catch errors in event handlers or async code as this is standard React behavior requiring no workaround
