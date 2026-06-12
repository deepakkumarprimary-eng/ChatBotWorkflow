# Requirements Document

## Introduction

This feature transforms the Chatbot Workflow Builder's visual identity from a light glassmorphism theme into a dark, futuristic, developer IDE aesthetic. The design draws inspiration from premium developer tools like AWS Kiro IDE — featuring deep dark backgrounds, electric neon accent glows, frosted dark glass surfaces, and subtle animations that convey a high-end developer experience. The implementation modifies the token system (tokens.css), adds targeted CSS enhancements for glow effects, animations, and code typography, and introduces a Semantic DOM Contract Layer — a set of `data-*` attributes added to TSX elements that encode node type, execution state, and UI role. CSS styling depends on these semantic attribute selectors (e.g., `[data-node-type="apiCall"]`, `[data-execution-state="selected"]`) rather than fragile class-name inference, providing a stable contract between the component layer and the styling layer.

## Glossary

- **Token_System**: The set of CSS custom properties defined at the `:root` level in `tokens.css` that control all visual values (colors, spacing, typography, shadows, glass effects)
- **Glass_Surface**: A UI panel (Toolbar, Palette, Property_Panel, dialogs) rendered with a semi-transparent dark background combined with a `backdrop-filter: blur()` effect to create a frosted appearance
- **Neon_Glow**: A colored `box-shadow` halo effect applied to interactive elements (nodes, buttons, palette items) using accent colors at 15-25% opacity to simulate a luminous highlight
- **Canvas**: The central workflow editing area rendered by the Canvas_Library where nodes and transitions are displayed
- **State_Node**: A visual node on the Canvas representing a workflow state, rendered by the StateNode component with type-specific accent colors
- **Code_Font**: A monospace font stack (JetBrains Mono, Fira Code, Cascadia Code, monospace) used for code-like UI elements such as state type labels and variable names
- **Execution_Animation**: CSS keyframe animations applied to State_Node elements during workflow execution to indicate running, completed, or errored status
- **Reduced_Motion**: The `prefers-reduced-motion: reduce` media query that disables or minimizes animations for users who have requested reduced motion in their OS settings
- **Semantic_DOM_Contract**: A set of `data-*` attributes added to TSX elements that encode node type, execution state, and UI role — serving as a stable contract between the component layer and the styling layer

## Requirements

### Requirement 1: Dark Mode Token System

**User Story:** As a developer using the workflow builder, I want the application to use a dark color palette, so that the interface reduces eye strain and feels like a premium IDE.

#### Acceptance Criteria

1. THE Token_System SHALL define background color tokens using values in the #0d1117 to #1a1b2e hex range for `--color-background` and `--color-canvas-bg`
2. THE Token_System SHALL define surface color tokens using RGBA values with RGB channels at or below 30 (decimal) and alpha between 0.60 and 0.85 for `--color-surface` and `--glass-bg` variants
3. THE Token_System SHALL define a primary text color token that achieves a minimum contrast ratio of 7:1 against the darkest background token value as measured by the WCAG 2.1 relative luminance formula
4. THE Token_System SHALL define a secondary text color token that achieves a minimum contrast ratio of 4.5:1 against the darkest background token value as measured by the WCAG 2.1 relative luminance formula
5. THE Token_System SHALL preserve the existing 5-shade scale structure (lightest, light, base, dark, darkest) for primary, secondary, success, warning, error, and neutral color groups, with each shade achieving a minimum contrast ratio of 3:1 against the `--color-background` token value
6. THE Token_System SHALL define glow accent tokens (`--glow-primary`, `--glow-secondary`, `--glow-success`, `--glow-warning`, `--glow-error`) as box-shadow values using the respective accent color at 15-25% opacity
7. THE Token_System SHALL define canvas-specific tokens with `--color-canvas-bg` set to a dark value (#161b22) and `--color-canvas-dot` set to a subtle grid color (#30363d)
8. THE Token_System SHALL define border color tokens (`--color-border`, `--color-border-strong`) as semi-transparent white RGBA values in the 0.06-0.12 opacity range
9. THE Token_System SHALL define a `--color-surface-solid` token as an opaque dark color with lightness within 5% of the `--color-surface` token's effective lightness for elements that cannot use transparency
10. THE Token_System SHALL preserve all existing CSS custom property names defined in `tokens.css` so that components referencing those tokens continue to resolve values without modification

### Requirement 2: Dark Glass Surface Effects

**User Story:** As a user, I want the application panels to have a frosted dark glass appearance, so that the interface feels layered and modern.

#### Acceptance Criteria

1. THE Theme_System SHALL define `--glass-bg` as an RGBA value with RGB channels each ≤ 40 and alpha between 0.60 and 0.75, combined with `--glass-blur` of `blur(12px)`, for use on Toolbar, Palette, and Property_Panel surfaces
2. THE Theme_System SHALL define `--glass-bg-strong` as an RGBA value with RGB channels each ≤ 40 and alpha between 0.80 and 0.90, for use on modal dialog surfaces (SaveWorkflowDialog, ConfirmDeleteDialog)
3. THE Theme_System SHALL define `--glass-border` as an RGBA value with RGB channels of 255 and alpha between 0.08 and 0.15 for Glass_Surface border accents
4. WHEN a dialog overlay is displayed, THE dialog backdrop SHALL use a background of rgba(0, 0, 0, alpha) with alpha between 0.60 and 0.80
5. THE Toolbar, Palette, and Property_Panel surfaces SHALL maintain a minimum text contrast ratio of 4.5:1 for text below 18pt (or below 14pt bold) and 3:1 for text at or above 18pt (or at or above 14pt bold), measured against the `--glass-bg` token value composited over a solid black (#000000) assumed backdrop

### Requirement 3: Neon Glow Interactions

**User Story:** As a user, I want interactive elements to glow with neon accents when I interact with them, so that the interface provides clear visual feedback with a futuristic aesthetic.

#### Acceptance Criteria

1. WHEN a State_Node has `data-execution-state="selected"`, THE State_Node SHALL display a Neon_Glow box-shadow with a blur radius between 8px and 16px and a spread radius between 0px and 4px, using the accent color corresponding to that node's `data-node-type` value at 15-25% opacity
2. WHEN a State_Node is hovered, THE State_Node SHALL shift its border color to the accent color corresponding to that node's `data-node-type` value at 30-50% opacity, with a CSS transition duration between 150ms and 200ms using ease or ease-in-out timing, targeted via `[data-node-type="..."]` attribute selectors
3. WHEN a toolbar button receives keyboard focus, THE toolbar button SHALL display a Neon_Glow ring with a spread radius between 2px and 4px using the primary accent color at 15-25% opacity (the execute button is targeted via `[data-role="execute"]`)
4. WHEN a palette item is hovered, THE palette item SHALL display a Neon_Glow box-shadow with a blur radius between 6px and 12px using the accent color associated with that state type at 15-25% opacity
5. THE Neon_Glow state changes SHALL use CSS `transition` properties with a duration between 120ms and 200ms and `ease` or `ease-in-out` timing functions for all glow transitions
6. WHEN the user has enabled reduced motion in their operating system, THE Neon_Glow transitions SHALL be replaced with an instant state change (0ms duration) using the `@media (prefers-reduced-motion: reduce)` query

### Requirement 4: Typography and Code Aesthetic

**User Story:** As a developer, I want code-like UI elements to use a monospace font, so that the interface reinforces the developer tool aesthetic.

#### Acceptance Criteria

1. THE Token_System SHALL define a `--font-family-code` custom property with the value `'JetBrains Mono', 'Fira Code', 'Cascadia Code', monospace`
2. WHEN a state type label is rendered in the `.type` element of a State_Node, THE label SHALL use the `--font-family-code` font stack
3. WHEN a variable name is displayed or edited in the Property_Panel (including context variable name inputs and the Input state's variable name field), THE variable name text SHALL use the `--font-family-code` font stack, targeted using the `[data-role="variable-name"]` selector
4. THE Token_System SHALL preserve the existing `--font-family` sans-serif token (`'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', system-ui, sans-serif`) as the primary UI font for all non-code text
5. WHEN a `prefers-color-scheme: dark` media query matches OR a `[data-theme="dark"]` attribute is set on the document root, THE Token_System SHALL redefine all surface, background, border, and neutral color tokens with dark-adapted values, maintaining the same token names as the light theme
6. IF the dark theme is active, THEN THE Token_System SHALL ensure all text rendered against dark background surfaces meets a minimum WCAG AA contrast ratio of 4.5:1 for normal text (below 18px) and 3:1 for large text (18px and above)

### Requirement 5: Animated State Indicators

**User Story:** As a user monitoring workflow execution, I want state nodes to display animated indicators during execution, so that I can clearly see which states are running, completed, or errored.

#### Acceptance Criteria

1. WHILE a State_Node has `data-execution-state="executing"`, THE State_Node SHALL display a pulsing Neon_Glow animation using CSS `@keyframes` with a cycle duration between 2000ms and 3000ms and `ease-in-out` timing function, targeted via the `[data-execution-state="executing"]` attribute selector
2. WHEN a State_Node transitions to `data-execution-state="completed"`, THE State_Node SHALL display a success glow using the `--glow-success` token color that fades from full intensity to zero opacity within 500ms using a CSS `animation` with `forwards` fill mode, targeted via the `[data-execution-state="completed"]` attribute selector
3. WHEN a State_Node transitions to `data-execution-state="errored"`, THE State_Node SHALL display a static red Neon_Glow border using the error accent color at 20-25% opacity that persists until the execution state changes, targeted via the `[data-execution-state="errored"]` attribute selector
4. THE Execution_Animation SHALL use pure CSS animations (`@keyframes` and `animation` properties) with no JavaScript `setTimeout`, `setInterval`, or `requestAnimationFrame` calls
5. WHEN the user has enabled Reduced_Motion in their operating system, THE Execution_Animation SHALL be disabled and replaced with a static colored border indicator using the `@media (prefers-reduced-motion: reduce)` query with `animation: none` applied to all animated State_Node elements

### Requirement 6: Canvas Background Enhancement

**User Story:** As a user, I want the canvas to have a subtle dark grid pattern, so that the editing surface feels structured and distinct from surrounding panels.

#### Acceptance Criteria

1. THE Canvas SHALL render a background color of #161b22 with a dot-grid pattern created using CSS `radial-gradient` circular dots of 1px radius at 20px intervals in both horizontal and vertical directions
2. THE Canvas dot-grid pattern SHALL use a dot color that achieves a contrast ratio between 1.5:1 and 3:1 (inclusive) against the canvas background color (#161b22), as calculated by the WCAG relative luminance formula
3. THE Token_System SHALL define a `--color-background-mesh` CSS custom property containing a linear-gradient overlay where each color stop uses an alpha value between 0.02 and 0.05 (2% to 5% opacity)
4. THE Canvas background color SHALL differ from the nearest adjacent panel surface color by a minimum contrast ratio of 1.3:1 as calculated by the WCAG relative luminance formula

### Requirement 7: Maintain All Existing Functional Behavior

**User Story:** As a developer, I want the dark theme to be a purely visual change that does not break existing functionality, so that all tests and build processes remain green.

#### Acceptance Criteria

1. WHEN `npm test` is executed from the `frontend/` directory, THE test runner SHALL exit with code 0 with all existing test suites and test cases passing without modification to any file within `src/__tests__/` or any `*.test.ts`/`*.test.tsx` file
2. WHEN `npm run build` is executed from the `frontend/` directory, THE build SHALL complete with exit code 0, zero TypeScript errors, and zero TypeScript warnings
3. THE dark theme implementation SHALL meet WCAG 2.1 AA contrast requirements (minimum 4.5:1 ratio for text below 18px regular or 14px bold, minimum 3:1 ratio for text at or above 18px regular or 14px bold) for all text elements against their immediate parent background color
4. THE implementation SHALL preserve the existing CSS module file structure by modifying only CSS custom property values in `tokens.css` and appending new CSS rules to existing `.module.css` files, without creating new files, deleting files, or renaming files
5. THE implementation SHALL only modify `.ts` or `.tsx` files to add `data-*` attributes (data-node-type, data-execution-state, data-role) and `aria-*` attributes (aria-busy) to existing JSX elements. No changes to component logic, event handlers, state management, hooks, props interfaces, or data flow are permitted.

### Requirement 8: Semantic DOM Contract Layer

**User Story:** As a developer implementing the dark theme, I want a stable set of semantic data attributes on DOM elements, so that CSS selectors target elements by meaning rather than by fragile class names.

#### Acceptance Criteria

1. THE State_Node root element SHALL include a `data-node-type` attribute set to the lowercase state type identifier (e.g., "apiCall", "condition", "response", "input", "wait", "parallel", "end")
2. THE State_Node root element SHALL include a `data-execution-state` attribute reflecting the current execution status (e.g., "idle", "executing", "completed", "errored", "selected")
3. WHEN a State_Node is selected, THE `data-execution-state` attribute SHALL be set to "selected" (or the active execution status if the node is currently executing)
4. THE State_Node component SHALL include key interactive elements with `data-role` attributes for styling hooks: toolbar execute button (`data-role="execute"`), variable name inputs (`data-role="variable-name"`)
5. THE State_Node component SHALL include `aria-busy="true"` on the root element when the node's execution state is "executing"
6. ALL CSS selectors in `.module.css` files that target node types SHALL use `[data-node-type="..."]` attribute selectors instead of class-based selectors (e.g., `.apiCall`, `.condition`)
7. ALL CSS selectors for execution and selection state SHALL use `[data-execution-state="..."]` attribute selectors instead of class-based selectors (e.g., `.selected`, `.executing`, `.completed`, `.errored`)
8. THE variable name inputs SHALL be targeted using `[data-role="variable-name"]` attribute selector instead of `[data-variable-name]` attribute selector
