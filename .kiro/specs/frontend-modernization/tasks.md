# Implementation Plan: Frontend Modernization

## Overview

This implementation migrates the Chatbot Workflow Builder frontend from `reactflow` v11 to `@xyflow/react` v12, introduces a CSS module system backed by design tokens, modernizes visual design, aligns React patterns with React 18 best practices, decomposes App.tsx state into focused custom hooks, adds application-level error boundaries, and ensures accessibility for all interactive controls. Tasks are ordered so that foundational changes (packages, tokens, types) land first, followed by component-level migration, styling, state decomposition, React best practices, error boundaries, accessibility, and finally testing verification.

## Tasks

- [x] 1. Update package dependencies and create foundational files
  - [x] 1.1 Replace reactflow with @xyflow/react in package.json
    - Remove `"reactflow": "^11.10.0"` from dependencies
    - Add `"@xyflow/react": "^12.0.0"` to dependencies
    - Run `npm install` to update node_modules and lockfile
    - _Requirements: 1.1, 1.10_

  - [x] 1.2 Create design tokens stylesheet (`src/styles/tokens.css`)
    - Create `src/styles/` directory
    - Define all CSS custom properties at `:root` level: colors (primary, secondary, success, warning, error, neutral scales with 5 shades each), spacing (4px base scale), border-radius (sm, md, lg, full), elevation shadows (none, low, medium, high), typography (font-size sm/base/md/lg, font-weight regular/medium/semibold), and surface colors
    - Import `tokens.css` in `src/main.tsx` before other styles
    - _Requirements: 2.1, 2.3, 2.4, 2.5, 2.6, 2.11_

  - [x] 1.3 Create library-specific type aliases (`src/types/reactflow.types.ts`)
    - Define `AppNode = Node<StateNodeData, 'stateNode'>` using `@xyflow/react` generics
    - Define `AppEdge = Edge` type alias
    - Export `workflowStateToNode` and `transitionToEdge` converter function signatures
    - _Requirements: 1.5, 5.1_

  - [x] 1.4 Add CSS module type declaration
    - Create or update `src/vite-env.d.ts` with `declare module '*.module.css'` declaration
    - Ensure TypeScript resolves CSS module imports as `Record<string, string>`
    - _Requirements: 2.2, 4.6_

- [x] 2. Migrate canvas components to @xyflow/react
  - [x] 2.1 Migrate WorkflowCanvas.tsx imports and API usage
    - Replace all `import ... from 'reactflow'` with `import ... from '@xyflow/react'`
    - Change `import ReactFlow from 'reactflow'` to `import { ReactFlow } from '@xyflow/react'`
    - Replace `import 'reactflow/dist/style.css'` with `import '@xyflow/react/dist/style.css'`
    - Update `NodeTypes` import to use `type` keyword: `import { type NodeTypes } from '@xyflow/react'`
    - Update node type generics to use `AppNode` type where applicable
    - Verify `screenToFlowPosition`, `onNodesChange`, `onConnect` usage matches v12 API
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.8, 1.9, 1.10_

  - [x] 2.2 Migrate StateNode.tsx imports
    - Replace `import { Handle, Position, NodeProps } from 'reactflow'` with `import { Handle, Position, type NodeProps } from '@xyflow/react'`
    - Verify `NodeProps<StateNodeData>` generic still compiles correctly
    - _Requirements: 1.5_

  - [x] 2.3 Migrate ZoomControls.tsx imports
    - Replace `import { useReactFlow } from 'reactflow'` with `import { useReactFlow } from '@xyflow/react'`
    - Verify `setViewport`, `getViewport`, `fitView` API usage unchanged
    - _Requirements: 1.7_

  - [x] 2.4 Update any remaining reactflow imports across the codebase
    - Search all `src/` files for any remaining `'reactflow'` import strings
    - Update any found imports to `'@xyflow/react'`
    - Confirm zero `reactflow` imports remain
    - _Requirements: 1.1_

  - [x] 2.5 Verify build passes after canvas migration
    - Run `npm run build` and confirm exit code 0 with zero TypeScript errors
    - Run `npm test` and confirm all existing tests pass
    - _Requirements: 6.1, 6.4_

- [x] 3. Checkpoint - Verify canvas migration
  - Ensure all tests pass, ask the user if questions arise.

- [x] 4. Create CSS modules for canvas components
  - [x] 4.1 Create WorkflowCanvas.module.css and apply to WorkflowCanvas.tsx
    - Define styles for canvas wrapper, dot grid background, and layout
    - Use token references for all colors (canvas background, dot color)
    - Replace any inline styles in WorkflowCanvas.tsx with CSS module classes
    - Set dot grid with contrast ratio ≥1.5:1 against canvas background
    - _Requirements: 2.2, 2.7, 3.9_

  - [x] 4.2 Create StateNode.module.css and apply to StateNode.tsx
    - Define styles for node container, gradient background, border-radius (medium token), drop shadow
    - Add selected state: 2px primary border, animated outline ring (150–300ms transition)
    - Add hovered state: medium elevation shadow with 150ms transition
    - Replace any inline styles in StateNode.tsx with CSS module classes
    - _Requirements: 2.2, 2.7, 3.5, 3.6, 3.7_

  - [x] 4.3 Create ZoomControls.module.css and apply to ZoomControls.tsx
    - Define styles for zoom control container and buttons
    - Use token references for colors and spacing
    - Replace any inline styles with CSS module classes
    - _Requirements: 2.2, 2.7_

  - [x] 4.4 Create ConfirmDeleteDialog.module.css and apply to ConfirmDeleteDialog.tsx
    - Define styles for dialog overlay, card, buttons
    - Use token references for all colors, spacing, radius, and shadows
    - Replace any inline styles with CSS module classes
    - _Requirements: 2.2, 2.7_

  - [x] 4.5 Create ConnectionToast.module.css and apply to ConnectionToast.tsx
    - Define styles for toast container, message, and dismiss
    - Use token references for colors
    - Replace any inline styles with CSS module classes
    - _Requirements: 2.2, 2.7_

- [x] 5. Create CSS modules for palette, panel, and workflow components
  - [x] 5.1 Create ComponentPalette.module.css and apply to ComponentPalette.tsx
    - Define styles for palette container, header, palette items as cards
    - Add hover elevation transition (150–250ms ease/ease-in-out)
    - Add collapsible section header with chevron rotation
    - Use token references for all colors and spacing
    - Replace any inline styles with CSS module classes
    - _Requirements: 2.2, 2.7, 2.9, 3.3, 3.4_

  - [x] 5.2 Create PropertyPanel.module.css and apply to PropertyPanel.tsx
    - Define card-based layout with 1px solid dividers between sections
    - Use typography tokens for label font-size and font-weight
    - Use token references for all colors, spacing, radius
    - Replace any inline styles with CSS module classes
    - _Requirements: 2.2, 2.7, 2.10, 3.8_

  - [x] 5.3 Create ExecutionMonitor.module.css and apply to ExecutionMonitor.tsx
    - Define styles for execution status display, history list
    - Use token references for all colors
    - Replace any inline styles with CSS module classes
    - _Requirements: 2.2, 2.7_

  - [x] 5.4 Create ValidationPanel.module.css and apply to ValidationPanel.tsx
    - Define styles for validation results, error/warning items
    - Use token references for all colors
    - Replace any inline styles with CSS module classes
    - _Requirements: 2.2, 2.7_

  - [x] 5.5 Create SaveWorkflowDialog.module.css and WorkflowListPanel.module.css
    - Define styles for dialog and list panel components
    - Use token references for all colors
    - Replace any inline styles with CSS module classes
    - _Requirements: 2.2, 2.7_

- [x] 6. Modernize application shell and toolbar
  - [x] 6.1 Extract Toolbar component and create Toolbar.module.css
    - Extract toolbar/header into `src/components/toolbar/Toolbar.tsx`
    - Create `src/components/toolbar/Toolbar.module.css` with backdrop-filter blur, 1px bottom border using neutral token
    - Style icon buttons with hover background change and visible `:focus-visible` outline using design tokens
    - Define `ToolbarProps` interface for all action handlers
    - _Requirements: 2.2, 2.7, 2.8, 3.1, 3.2_

  - [x] 6.2 Create App.module.css and modernize App.tsx layout
    - Define app shell layout: flex column, 100vw × 100vh
    - Apply neutral background with ≥3:1 contrast ratio against panel surfaces
    - Replace any inline styles in App.tsx with CSS module classes
    - Import and use extracted Toolbar component
    - _Requirements: 2.2, 2.7, 3.10_

- [x] 7. Checkpoint - Verify styling migration
  - Ensure all tests pass, ask the user if questions arise.

- [x] 8. Decompose App.tsx state into custom hooks
  - [x] 8.1 Create useWorkflowPersistence hook (`src/hooks/useWorkflowPersistence.ts`)
    - Extract currentWorkflowId, currentWorkflowName, currentWorkflowDescription, saveDialogVisible, and listPanelVisible state from App.tsx
    - Move handleSaveClick, handleSaveWorkflow, handleOpenWorkflow, handleNewWorkflow, handleExport, handleImportFile callbacks into the hook
    - Define `UseWorkflowPersistenceOptions` interface accepting canvasState, showToast, setInitialStates, setInitialTransitions, setInitialContextVars, setContextVariables, setCanvasKey, setSelectedState
    - Return combined state and actions object matching the current API surface
    - _Requirements: 7.1, 7.3, 7.5_

  - [x] 8.2 Create useExecutionState hook (`src/hooks/useExecutionState.ts`)
    - Extract activeExecutionId and executionListVisible state from App.tsx
    - Move handleExecute callback into the hook
    - Define `UseExecutionStateOptions` interface accepting currentWorkflowId and showToast
    - Return combined state and actions object
    - _Requirements: 7.2, 7.3_

  - [x] 8.3 Refactor App.tsx to use extracted hooks
    - Replace extracted state and callbacks with `useWorkflowPersistence(...)` and `useExecutionState(...)` calls
    - Verify App.tsx has at most 5 direct useState/useReducer calls
    - Pass `persistence.*` and `execution.*` values to child components
    - Confirm no behavior changes — all toolbar buttons, dialogs, and panels work identically
    - _Requirements: 7.1, 7.2, 7.3, 7.5_

  - [ ]* 8.4 Write unit tests for useWorkflowPersistence and useExecutionState hooks
    - Test useWorkflowPersistence with `renderHook` from @testing-library/react-hooks
    - Test useExecutionState with `renderHook`
    - Verify state transitions, callback invocations, and error handling
    - _Requirements: 7.4_

- [x] 9. Apply React 18 best practices
  - [x] 9.1 Audit and fix useCallback/useMemo usage across components
    - Wrap callbacks passed to child components in `useCallback` with accurate dependency arrays
    - Wrap derived computations (iterations, repeated computations) in `useMemo` with accurate dependency arrays
    - Ensure no stale closures from incorrect dependency arrays
    - _Requirements: 4.2, 4.3_

  - [x] 9.2 Audit and fix list keys and event handler types
    - Ensure all list renders use stable, unique keys derived from item identity (not array index)
    - Update event handler prop types to use React's built-in event types (`React.MouseEvent`, `React.ChangeEvent`, etc.)
    - _Requirements: 4.1, 4.5_

  - [x] 9.3 Audit and eliminate class components and `any` annotations
    - Confirm zero class components in `src/` (excluding node_modules, .d.ts files, and ErrorBoundary which is exempted per Req 9)
    - Remove or replace any `any` type annotations in component props and hook return types
    - _Requirements: 4.4, 4.8_

  - [x] 9.4 Audit useEffect cleanup and state consolidation
    - Ensure all useEffect hooks that subscribe to external resources return cleanup functions
    - Consolidate components with 3+ related useState calls into useReducer or custom hooks where one update requires reading another
    - _Requirements: 4.7, 4.9_

  - [x] 9.5 Verify TypeScript strict mode compilation
    - Run `npm run build` and confirm zero TypeScript errors and warnings
    - _Requirements: 4.6, 6.4_

- [x] 10. Checkpoint - Verify state decomposition and React best practices
  - Ensure all tests pass, ask the user if questions arise.

- [x] 11. Implement error boundaries
  - [x] 11.1 Create base ErrorBoundary class component (`src/components/errors/ErrorBoundary.tsx`)
    - Implement class component with `getDerivedStateFromError` and `componentDidCatch`
    - Accept `children`, `fallback` (ReactNode or render function), and optional `onError` callback props
    - Expose `resetErrorBoundary` method to clear error state
    - Log error details to `console.error` without exposing stack traces in UI
    - _Requirements: 9.3, 9.6_

  - [x] 11.2 Create CanvasErrorBoundary (`src/components/errors/CanvasErrorBoundary.tsx`)
    - Wrap WorkflowCanvas with ErrorBoundary using a canvas-specific fallback
    - Display "Canvas Error" heading, user-friendly message, and "Reset" button
    - On Reset click: call `onReset` prop (connected to handleNewWorkflow) to re-mount canvas with empty state, then call `resetErrorBoundary`
    - _Requirements: 9.1, 9.4_

  - [x] 11.3 Create AppErrorBoundary (`src/components/errors/AppErrorBoundary.tsx`)
    - Wrap entire App in main.tsx with a top-level ErrorBoundary
    - Display "Something went wrong" heading, user-friendly message, and "Reload" button
    - On Reload click: call `window.location.reload()`
    - _Requirements: 9.2, 9.5_

  - [x] 11.4 Create ErrorBoundary.module.css and integrate boundaries into app
    - Style canvasFallback and appFallback layouts using design tokens
    - Import CanvasErrorBoundary in App.tsx wrapping WorkflowCanvas
    - Import AppErrorBoundary in main.tsx wrapping `<App />`
    - _Requirements: 9.1, 9.2, 9.3_

  - [ ]* 11.5 Write unit tests for error boundary components
    - Test ErrorBoundary catches rendering errors and shows fallback
    - Test CanvasErrorBoundary shows Reset button and calls onReset
    - Test AppErrorBoundary shows Reload button
    - _Requirements: 9.1, 9.2, 9.3_

- [x] 12. Implement accessibility enhancements
  - [x] 12.1 Add aria-labels to Toolbar icon-only buttons
    - Add `aria-label` to every icon-only button in the Toolbar (e.g., Undo, Redo)
    - Ensure buttons with visible text do NOT have a redundant aria-label
    - Verify all toolbar buttons are reachable via Tab key
    - _Requirements: 8.1, 8.2, 8.3_

  - [x] 12.2 Add ARIA roles and labels to ComponentPalette
    - Add `role="list"` and `aria-label="Workflow state types"` to palette container
    - Add `role="listitem"` to each draggable palette item
    - _Requirements: 8.4_

  - [x] 12.3 Add role="application" and aria-label to canvas area
    - Wrap canvas area div with `role="application"` and `aria-label="Workflow editor canvas"`
    - _Requirements: 8.7_

  - [x] 12.4 Add label associations to PropertyPanel form fields
    - Associate each input/select with a `<label>` element using `htmlFor`/`id` pairing
    - Use `aria-label` for fields where visible labels are impractical
    - _Requirements: 8.5_

  - [x] 12.5 Implement useFocusTrap hook and apply to modal dialogs
    - Create `src/hooks/useFocusTrap.ts` implementing focus trapping logic
    - Trap focus within SaveWorkflowDialog when open; return focus to trigger on close
    - Trap focus within ConfirmDeleteDialog when open; return focus to trigger on close
    - _Requirements: 8.6_

  - [ ]* 12.6 Write unit tests for accessibility features
    - Test that icon-only buttons have aria-label attributes
    - Test that palette has correct ARIA roles
    - Test that useFocusTrap traps and releases focus correctly
    - _Requirements: 8.1, 8.4, 8.6_

- [x] 13. Verify functional parity and existing tests
  - [x] 13.1 Verify all existing property-based tests pass unchanged
    - Run `npm test` and confirm all suites in `src/__tests__/` pass
    - Confirm `canvas.properties.test.ts`, `preservation.properties.test.ts`, `range.properties.test.ts` pass with 100 runs per property
    - Confirm no test file modifications were needed
    - _Requirements: 6.1, 6.2, 6.5, 6.6_

  - [x] 13.2 Verify no reactflow remnants and build integrity
    - Search codebase for any remaining `reactflow` import strings — confirm zero found in `src/`
    - Run `npm run build` — confirm exit code 0, zero TS errors
    - Run `npm test` — confirm exit code 0, all suites pass
    - _Requirements: 1.1, 6.4_

- [ ] 14. Add new property-based tests
  - [ ]* 14.1 Write property test for CSS module token compliance (Property 1)
    - **Property 1: CSS modules use design token references for all colors**
    - Create `src/__tests__/cssModuleCompliance.properties.test.ts`
    - Use fast-check to generate file paths from component CSS modules, parse declarations, assert all color values use `var(--*)` references
    - Run with `{ numRuns: 100 }`
    - **Validates: Requirements 2.7**

  - [ ]* 14.2 Write property test for color scale completeness (Property 2)
    - **Property 2: Color scales contain at least 5 shades**
    - Create `src/__tests__/tokenCompleteness.properties.test.ts`
    - Use fast-check to generate color scale names from {primary, secondary, success, warning, error, neutral}, assert each has lightest/light/base/dark/darkest tokens
    - Run with `{ numRuns: 100 }`
    - **Validates: Requirements 2.3**

  - [ ]* 14.3 Write property test for theme contrast ratios (Property 3)
    - **Property 3: Theme contrast ratios meet minimum thresholds**
    - Create `src/__tests__/contrastRatio.properties.test.ts`
    - Implement WCAG relative luminance contrast calculation
    - Assert canvas dot vs canvas background ≥1.5:1 and shell background vs panel surface ≥3:1
    - Run with `{ numRuns: 100 }`
    - **Validates: Requirements 3.9, 3.10**

  - [ ]* 14.4 Write property test for default state factory (Property 4)
    - **Property 4: Default state factory produces correct config type**
    - Create `src/__tests__/stateFactory.properties.test.ts`
    - Use fast-check to generate arbitrary StateType values and positions, assert `createDefaultState` produces matching config discriminator
    - Run with `{ numRuns: 100 }`
    - **Validates: Requirements 5.1**

  - [ ]* 14.5 Write property test for serialization round-trip (Property 5)
    - **Property 5: Serialization round-trip preserves workflow definition**
    - Extend `src/__tests__/preservation.properties.test.ts`
    - Use fast-check to generate arbitrary CanvasState, serialize to WorkflowDefinition and deserialize back, assert equivalence
    - Run with `{ numRuns: 100 }`
    - **Validates: Requirements 5.5, 5.7, 5.8**

  - [ ]* 14.6 Write property test for canvas state preservation on failure (Property 6)
    - **Property 6: Canvas state preserved on failed operations**
    - Extend `src/__tests__/preservation.properties.test.ts`
    - Use fast-check to generate arbitrary CanvasState plus failing operations, assert state unchanged after failure
    - Run with `{ numRuns: 100 }`
    - **Validates: Requirements 5.6, 5.10**

  - [ ]* 14.7 Write property test for import validation (Property 7)
    - **Property 7: Import validation accepts valid workflows and rejects invalid inputs**
    - Create `src/__tests__/importValidation.properties.test.ts`
    - Use fast-check to generate valid WorkflowDefinition JSON (assert acceptance) and invalid inputs (assert rejection with error message)
    - Run with `{ numRuns: 100 }`
    - **Validates: Requirements 5.9, 5.10**

  - [ ]* 14.8 Write property test for transition condition derivation (Property 8)
    - **Property 8: Transition condition correctly derived from source handle**
    - Extend `src/__tests__/canvas.properties.test.ts`
    - Use fast-check to generate source state types and handle IDs, assert condition field set correctly based on output count
    - Run with `{ numRuns: 100 }`
    - **Validates: Requirements 5.2**

- [x] 15. Final checkpoint - Full verification
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation at key milestones
- The stable utility layer (`canvasOperations.ts`, `canvas.types.ts`, `useUndoRedo.ts`) is intentionally not modified — existing property tests validate this invariant
- Property tests validate universal correctness properties from the design document
- Unit tests validate specific examples and edge cases
- The `reactflow` package is fully removed in task 1.1; all subsequent work uses `@xyflow/react` only
- ErrorBoundary is the single exception to the "no class components" rule (React requires class components for error boundaries)
- State decomposition (task 8) simplifies App.tsx before React 18 best practices audit (task 9)
- Error boundaries (task 11) are placed after canvas migration and styling are stable
- Accessibility (task 12) is applied after all visual components are in their final form

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2", "1.4"] },
    { "id": 1, "tasks": ["1.3", "2.1"] },
    { "id": 2, "tasks": ["2.2", "2.3", "2.4"] },
    { "id": 3, "tasks": ["2.5"] },
    { "id": 4, "tasks": ["4.1", "4.2", "4.3", "4.4", "4.5", "5.1", "5.2", "5.3", "5.4", "5.5"] },
    { "id": 5, "tasks": ["6.1"] },
    { "id": 6, "tasks": ["6.2"] },
    { "id": 7, "tasks": ["8.1", "8.2"] },
    { "id": 8, "tasks": ["8.3"] },
    { "id": 9, "tasks": ["8.4", "9.1", "9.2", "9.3", "9.4"] },
    { "id": 10, "tasks": ["9.5"] },
    { "id": 11, "tasks": ["11.1"] },
    { "id": 12, "tasks": ["11.2", "11.3"] },
    { "id": 13, "tasks": ["11.4"] },
    { "id": 14, "tasks": ["11.5", "12.1", "12.2", "12.3", "12.4"] },
    { "id": 15, "tasks": ["12.5"] },
    { "id": 16, "tasks": ["12.6", "13.1", "13.2"] },
    { "id": 17, "tasks": ["14.1", "14.2", "14.3", "14.4", "14.5", "14.6", "14.7", "14.8"] }
  ]
}
```
