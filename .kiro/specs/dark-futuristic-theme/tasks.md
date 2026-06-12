# Implementation Plan: Dark Futuristic Theme

## Overview

Transform the Chatbot Workflow Builder from a light glassmorphism theme to a dark, futuristic, developer IDE aesthetic using a three-layer approach: Token Layer (tokens.css value changes), Semantic DOM Contract Layer (data-*/aria-* attributes on TSX elements), and Styling Layer (appended CSS rules using attribute selectors in existing .module.css files). No new files are created or deleted. TSX changes are limited to attribute additions only.

## Tasks

- [x] 1. Update Token Layer — Dark color values in tokens.css
  - [x] 1.1 Overwrite existing color token values with dark-adapted palette
    - Replace all color values in the `:root` block of `frontend/src/styles/tokens.css` with dark theme values
    - Overwrite primary, secondary, success, warning, error, and neutral color scales
    - Overwrite glass-bg, glass-bg-strong, glass-border, glass-border-subtle values
    - Overwrite surface, background, border, canvas, and shadow tokens
    - Preserve all existing CSS custom property names — only values change
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.7, 1.8, 1.9, 1.10, 2.1, 2.2, 2.3_

  - [x] 1.2 Add new glow, code font, and mesh tokens
    - Add `--glow-primary`, `--glow-secondary`, `--glow-success`, `--glow-warning`, `--glow-error` box-shadow tokens
    - Add `--font-family-code` with JetBrains Mono / Fira Code / Cascadia Code / monospace fallback stack
    - Add `--color-background-mesh` linear-gradient token
    - Add `--color-surface-solid` opaque dark color token
    - Add `--color-canvas-bg` and `--color-canvas-dot` canvas-specific tokens (if not already present from 1.1)
    - _Requirements: 1.6, 1.7, 4.1, 6.3_

- [x] 2. Implement Semantic DOM Contract Layer — TSX attribute additions
  - [x] 2.1 Add data-node-type, data-execution-state, and aria-busy attributes to StateNode.tsx
    - Add `data-node-type={data.stateType.toLowerCase().replace('_', '')}` to root div
    - Add `data-execution-state={selected ? 'selected' : 'idle'}` to root div
    - Add `aria-busy={false}` to root div (true when executing)
    - No logic changes — attributes only
    - _Requirements: 8.1, 8.2, 8.3, 8.5_

  - [x] 2.2 Add data-role="execute" attribute to Toolbar.tsx execute button
    - Add `data-role="execute"` to the execute button element
    - No logic changes — attribute only
    - _Requirements: 8.4_

  - [x] 2.3 Add data-role="variable-name" attribute to PropertyPanel.tsx inputs
    - Add `data-role="variable-name"` to variable name input elements
    - No logic changes — attribute only
    - _Requirements: 8.4, 8.8_

- [x] 3. Checkpoint — Verify build and tests pass after token and TSX changes
  - Ensure all tests pass, ask the user if questions arise.
  - Run `npm run build` and `npm test` from `frontend/` to confirm zero TypeScript errors and all tests green

- [x] 4. Implement StateNode glow and animation styles
  - [x] 4.1 Append neon glow rules for selected state (type-specific) to StateNode.module.css
    - Add `.node[data-node-type="apicall"][data-execution-state="selected"]` box-shadow rules for all 7 node types
    - Each rule uses the type-specific accent color at 22% opacity with 12px blur and 3px spread ring
    - _Requirements: 3.1, 8.6, 8.7_

  - [x] 4.2 Append hover border-color shift rules to StateNode.module.css
    - Add `.node[data-node-type="..."]:hover` border-color rules for all 7 node types
    - Each rule uses the type-specific accent color at 40% opacity
    - _Requirements: 3.2, 8.6_

  - [x] 4.3 Append code font rule for .type class to StateNode.module.css
    - Add `.type { font-family: var(--font-family-code); }` rule
    - _Requirements: 4.2_

  - [x] 4.4 Append execution animation keyframes and state rules to StateNode.module.css
    - Add `@keyframes executionPulse` (2.5s ease-in-out infinite cycle)
    - Add `@keyframes completionFade` (500ms fade-out with forwards fill)
    - Add `.node[data-execution-state="executing"]` animation rule
    - Add `.node[data-execution-state="completed"]` animation rule
    - Add `.node[data-execution-state="errored"]` static red glow rule
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 8.7_

  - [x] 4.5 Append reduced-motion media query block to StateNode.module.css
    - Add `@media (prefers-reduced-motion: reduce)` block
    - Disable all transitions with `transition: none`
    - Replace executing animation with static glow (`animation: none`)
    - Replace completed animation with static success glow
    - _Requirements: 3.6, 5.5_

- [x] 5. Implement remaining module CSS enhancements
  - [x] 5.1 Append dot-grid background rules to WorkflowCanvas.module.css
    - Add `.canvasWrapper` background-color, background-image (mesh + radial-gradient dot grid), and background-size rules
    - Use `var(--color-canvas-bg)`, `var(--color-background-mesh)`, `var(--color-canvas-dot)` tokens
    - _Requirements: 6.1, 6.2, 6.3, 6.4_

  - [x] 5.2 Append palette item hover glow to ComponentPalette.module.css
    - Add `.item:hover` box-shadow rule using primary accent at 18% opacity
    - _Requirements: 3.4_

  - [x] 5.3 Append focus glow ring rules to Toolbar.module.css
    - Add `.iconButton:focus-visible` glow ring rule
    - Add `.primaryButton:focus-visible` glow ring rule
    - Add `.executeButton:focus-visible, [data-role="execute"]:focus-visible` success-colored glow rule
    - _Requirements: 3.3, 8.4_

  - [x] 5.4 Append dark backdrop and dialog surface rules to SaveWorkflowDialog.module.css
    - Add `.overlay` dark backdrop rule (rgba(0,0,0,0.70))
    - Add `.dialog` dark surface with border rule
    - _Requirements: 2.4_

  - [x] 5.5 Append dark backdrop and dialog surface rules to ConfirmDeleteDialog.module.css
    - Add `.overlay` dark backdrop rule (rgba(0,0,0,0.70))
    - Add `.dialog` dark surface with border rule
    - _Requirements: 2.4_

  - [x] 5.6 Append code font rules to PropertyPanel.module.css
    - Add `[data-role="variable-name"]` rule with `font-family: var(--font-family-code)` and `font-size: var(--font-size-base)`
    - Add `.stateType { font-family: var(--font-family-code); }` rule
    - _Requirements: 4.3, 8.8_

- [x] 6. Final checkpoint — Verify complete implementation
  - Ensure all tests pass, ask the user if questions arise.
  - Run `npm run build` from `frontend/` to confirm zero CSS parse errors and zero TypeScript errors
  - Run `npm test` from `frontend/` to confirm all existing tests still pass
  - Verify no new files were created and no files were deleted

## Notes

- No property-based tests are included because this is a purely visual/CSS transformation with no testable business logic
- All CSS changes are additive — existing rules are never modified or removed
- TSX changes are limited to `data-*` and `aria-*` attribute additions only; no component logic is altered
- All new CSS selectors use attribute selectors (`[data-node-type]`, `[data-execution-state]`, `[data-role]`) rather than class-based selectors
- The implementation preserves all existing CSS custom property names — only their values change
- Checkpoints verify that `npm run build` and `npm test` both exit with code 0

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2"] },
    { "id": 1, "tasks": ["2.1", "2.2", "2.3"] },
    { "id": 2, "tasks": ["4.1", "4.2", "4.3", "4.4", "4.5", "5.1", "5.2", "5.3", "5.4", "5.5", "5.6"] }
  ]
}
```
