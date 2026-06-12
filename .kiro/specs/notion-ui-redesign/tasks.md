# Implementation Plan: Notion UI Redesign

## Overview

Transform the Chatbot Workflow Builder's visual layer from dark glassmorphism to a Notion-inspired design language. All changes are CSS tokens, CSS modules, icon imports, and className assignments — no logic, state management, or behavioral changes. The implementation proceeds bottom-up: foundation tokens first, then component styles, then new components (custom edge, icons), and finally tests.

## Tasks

- [x] 1. Design token overhaul and React Flow variable mapping
  - [x] 1.1 Rewrite `frontend/src/styles/tokens.css` with Notion-style tokens
    - Replace all color tokens with the light-mode Notion palette (surfaces: #ffffff, #f7f7f5; text: #2f3437, #6b6f76; borders: rgba(0,0,0,0.08/0.12); accent: #2383e2)
    - Replace shadows with soft low-opacity values (--shadow-sm, --shadow-md, --shadow-lg)
    - Remove all glassmorphism tokens (--glass-bg, --glass-bg-strong, --glass-border, --glass-blur, --glass-blur-strong)
    - Remove all neon glow tokens (--glow-primary, --glow-secondary, --glow-success, --glow-warning, --glow-error)
    - Remove --color-background-mesh
    - Add semantic indicator colors (--color-indicator-blue, --color-indicator-orange, --color-indicator-green, --color-indicator-purple, --color-indicator-red)
    - Set --transition-standard: 150ms ease-out, remove old transition tokens
    - Set --color-canvas-bg: #ffffff, --color-canvas-dot: rgba(0,0,0,0.06)
    - Add `.react-flow` scoped section mapping tokens to --xy-* variables (node bg, border, shadows, edge stroke, handle, selection, controls, background dots)
    - _Requirements: 1.1–1.12, 7.1, 7.2, 9.7_

- [x] 2. Node component visual redesign
  - [x] 2.1 Rewrite `frontend/src/components/canvas/nodes/StateNode.module.css` with flat card styling
    - Set uniform background #f7f7f5 for all state types (remove per-type gradient classes)
    - Add 3px left-border accent using CSS variable per state type for type differentiation
    - Set border-radius: 10px, padding: 12px, soft shadow (--shadow-sm)
    - On hover: darken border from rgba(0,0,0,0.08) to rgba(0,0,0,0.12), no glow or elevation change
    - On select: 1.5px accent-color outline layered on .react-flow__node.selected
    - Remove all neon glow keyframes (executionPulse, completionFade) and type-specific glow attribute selectors
    - Remove backdrop-filter from .node
    - Update handle styles to use Notion-style white bg with subtle border
    - Add @media (prefers-reduced-motion: reduce) to disable all transitions
    - _Requirements: 2.1–2.8, 2.10, 7.3, 7.4, 7.5, 9.4_

  - [x] 2.2 Update `frontend/src/components/canvas/nodes/StateNode.tsx` icon system
    - Add `lucide-react` dependency to `frontend/package.json`
    - Replace emoji STATE_TYPE_ICONS with Lucide React icon components (Globe, GitBranch, MessageSquare, FileInput, Clock, Layers, CircleStop)
    - Add STATE_TYPE_INDICATOR color mapping record for left-border CSS variable
    - Apply indicator color via inline style on the node container's borderLeft
    - Render Lucide icon with size={16}, strokeWidth={1.5}, color from CSS variable (--color-text-secondary, primary on hover/selected)
    - Preserve all existing data-testid, data-node-type, data-execution-state attributes
    - Preserve all Handle components with existing IDs, types, positions
    - _Requirements: 4.1–4.4, 8.1, 8.2_

- [x] 3. Custom edge component and canvas workspace
  - [x] 3.1 Create `frontend/src/components/canvas/edges/NotionEdge.tsx`
    - Implement custom edge component using BaseEdge + getBezierPath from @xyflow/react
    - Export NotionEdge function component with standard EdgeProps
    - Styling relies on React Flow CSS variables (--xy-edge-stroke-default: #d0d0d0, stroke-width ~1.25px)
    - No custom inline styles or glow effects
    - _Requirements: 3.1–3.6, 3.8, 9.5, 9.8_

  - [x] 3.2 Update `frontend/src/components/canvas/WorkflowCanvas.tsx` for new edge type and base CSS
    - Change import from `@xyflow/react/dist/style.css` to `@xyflow/react/dist/base.css`
    - Import NotionEdge component
    - Define `edgeTypes` as module-level constant: `{ notion: NotionEdge }`
    - Pass edgeTypes to ReactFlow component
    - Update transitionToEdge to set `type: 'notion'` and remove inline `style: { strokeWidth: 2 }`
    - Ensure nodeTypes remains defined at module level (already correct)
    - Update Background component: set gap={20} (currently 16)
    - _Requirements: 3.6, 3.7, 6.2, 6.6, 9.1, 9.3_

  - [x] 3.3 Rewrite `frontend/src/components/canvas/WorkflowCanvas.module.css`
    - Set canvasWrapper background-color: #ffffff (via token)
    - Remove background-image mesh gradient
    - Remove glassmorphism-based handle highlights
    - Update handle connecting/valid states to use accent color at low opacity
    - _Requirements: 6.1, 6.4, 6.5, 6.7_

- [x] 4. Toolbar and controls visual redesign
  - [x] 4.1 Rewrite `frontend/src/components/toolbar/Toolbar.module.css` with flat Notion styling
    - Set toolbar background: #ffffff, single bottom border rgba(0,0,0,0.08)
    - Remove backdrop-filter, glass-bg, glass-blur-strong
    - Button default: flat, no gradient, no box-shadow, subtle border
    - Button hover: light grey background tint (#f7f7f5), no elevation/transform
    - Button focus: 2px solid accent outline, 2px offset
    - Primary/Execute buttons: solid accent (#2383e2) background, white text, no gradient, no glow
    - Remove all neon glow focus rings
    - Preserve disabled state styling
    - _Requirements: 5.1–5.6, 8.3_

  - [x] 4.2 Update `frontend/src/components/canvas/ZoomControls.module.css` with Notion button style
    - Set container background: #ffffff, border: rgba(0,0,0,0.08), shadow: --shadow-sm
    - Button hover: #f7f7f5 background, border darkens slightly
    - Button focus: 2px accent outline, 2px offset
    - Use --transition-standard (150ms ease-out) for transitions
    - _Requirements: 6.8, 9.10_

  - [x] 4.3 Update `frontend/src/components/canvas/ConfirmDeleteDialog.module.css` with light surface styling
    - Set dialog background: #ffffff, soft shadow (--shadow-lg)
    - Border: rgba(0,0,0,0.08)
    - Remove any glassmorphism or dark theme styling
    - Button styling consistent with toolbar flat style
    - _Requirements: 1.6, 5.3_

- [x] 5. Checkpoint — Verify build and visual consistency
  - Ensure all tests pass, ask the user if questions arise.
  - Run `npm run build` from frontend/ to confirm no TypeScript or CSS errors
  - Visually confirm the canvas renders with white background, dot grid, flat nodes, and thin edges

- [x] 6. Property-based and unit tests
  - [x]* 6.1 Write property test: Node visual uniformity with type-specific indicators
    - **Property 1: Node visual uniformity with type-specific indicators**
    - **Validates: Requirements 2.6, 2.7**
    - File: `frontend/src/__tests__/notion-ui-redesign.properties.test.ts`
    - Use fast-check `fc.constantFrom(...ALL_STATE_TYPES)` to generate random StateType
    - Render StateNode, assert uniform background #f7f7f5 (no type-specific gradient)
    - Assert presence of left-border indicator with a type-specific color

  - [x]* 6.2 Write property test: Icon system consistency
    - **Property 2: Icon system consistency**
    - **Validates: Requirements 4.1, 4.2, 4.4**
    - File: `frontend/src/__tests__/notion-ui-redesign.properties.test.ts`
    - For any StateType, render StateNode and assert icon is an SVG element (not emoji text)
    - Assert SVG has stroke-width="1.5" and fill="none" (outline-only)

  - [x]* 6.3 Write property test: Transition standard compliance
    - **Property 3: Transition standard compliance**
    - **Validates: Requirements 7.1, 7.2**
    - File: `frontend/src/__tests__/notion-ui-redesign.properties.test.ts`
    - Parse CSS module files (StateNode, Toolbar, WorkflowCanvas, ZoomControls) for transition declarations
    - Assert all durations are between 120ms and 180ms
    - Assert all timing functions are ease-out

  - [x]* 6.4 Write property test: Component structural preservation
    - **Property 4: Component structural preservation**
    - **Validates: Requirements 8.1, 8.2**
    - File: `frontend/src/__tests__/notion-ui-redesign.properties.test.ts`
    - For any StateType, render StateNode and assert data-testid and data-node-type attributes are present with correct values
    - Assert Handle elements (target-top, target-left, source-bottom/source-right or true/false) are present with correct props

  - [x]* 6.5 Write unit tests for token values and glassmorphism removal
    - File: `frontend/src/__tests__/notion-ui-redesign.test.ts`
    - Assert tokens.css contains expected values (#ffffff surface, #f7f7f5 node bg, #2383e2 accent)
    - Assert tokens.css does NOT contain glass-bg, glass-blur, backdrop-filter, glow-* tokens
    - Assert .react-flow section exists with --xy-* variable overrides
    - _Requirements: 1.1–1.9, 1.11, 1.12_

  - [x]* 6.6 Write unit tests for toolbar, canvas, and edge components
    - File: `frontend/src/__tests__/notion-ui-redesign.test.ts`
    - Assert Toolbar renders without backdrop-filter or gradient classes
    - Assert toolbar buttons retain all data-testid attributes
    - Assert NotionEdge renders a BaseEdge with bezier path
    - Assert WorkflowCanvas imports base.css (check edge type is 'notion')
    - Assert Background component has variant="dots" and gap={20}
    - _Requirements: 3.6, 5.1, 5.2, 6.2, 6.6, 8.3, 9.1_

- [x] 7. Final checkpoint — Full test suite
  - Ensure all tests pass, ask the user if questions arise.
  - Run `npm test` from frontend/ to execute all property-based and unit tests
  - Confirm zero regressions in existing test suite

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document
- Unit tests validate specific examples, edge cases, and absence of removed styles
- The `lucide-react` dependency is added during task 2.2 — run `npm install` before proceeding to later tasks
- All style changes consume design tokens; no raw color values in component CSS modules
- The `edgeTypes` and `nodeTypes` objects MUST remain at module level to avoid React Flow re-mount cycles

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["2.1", "3.3", "4.1", "4.2", "4.3"] },
    { "id": 2, "tasks": ["2.2", "3.1"] },
    { "id": 3, "tasks": ["3.2"] },
    { "id": 4, "tasks": ["6.1", "6.2", "6.3", "6.4", "6.5", "6.6"] }
  ]
}
```
