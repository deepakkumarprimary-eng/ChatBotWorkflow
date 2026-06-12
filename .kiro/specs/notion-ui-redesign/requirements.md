# Requirements Document

## Introduction

Redesign the visual UI layer of the Chatbot Workflow Builder to follow Notion's design philosophy: clean, minimal, calm aesthetics with soft neutral colors, subtle borders and shadows, and smooth but restrained interactions. This redesign covers only the presentation layer (CSS tokens, component styles, icon system, visual spacing) and explicitly excludes any changes to business logic, state management, or application behavior.

## Glossary

- **Design_Tokens**: CSS custom properties defined in `tokens.css` that provide the foundational color, spacing, typography, shadow, and transition values consumed by all component stylesheets.
- **Node_Component**: The `StateNode` React component and its associated CSS module that renders individual workflow state nodes on the canvas.
- **Edge**: The visual connection line between two nodes on the React Flow canvas representing a workflow transition.
- **Toolbar**: The application header bar containing workflow action buttons (New, Open, Save, Export, Import, Execute).
- **Canvas**: The React Flow workspace area where nodes and edges are rendered and manipulated.
- **Icon_System**: The set of visual icons used throughout the application to represent state types and toolbar actions.
- **Focus_Ring**: The visual indicator displayed around an interactive element when it receives keyboard focus or is selected.
- **React_Flow_CSS_Variables**: The set of built-in CSS custom properties (prefixed with `--xy-`) provided by @xyflow/react for theming nodes, edges, handles, backgrounds, controls, and selection indicators.
- **BaseEdge**: The React Flow component (`<BaseEdge />`) used as the foundation for rendering custom edge paths.
- **nodeTypes_Object**: The static object mapping node type strings to React components, passed to the React Flow `<ReactFlow>` component for custom node rendering.
- **edgeTypes_Object**: The static object mapping edge type strings to React components, passed to the React Flow `<ReactFlow>` component for custom edge rendering.

## Requirements

### Requirement 1: Design Token Overhaul

**User Story:** As a developer, I want the design tokens to reflect a Notion-like neutral palette, so that all components inherit a clean, minimal aesthetic without per-component color overrides.

#### Acceptance Criteria

1. THE Design_Tokens SHALL define a light-mode background color of #ffffff for the application surface.
2. THE Design_Tokens SHALL define a node background color of #f7f7f5 for canvas node surfaces.
3. THE Design_Tokens SHALL define border colors using rgba(0,0,0,0.08) for standard borders and rgba(0,0,0,0.12) for emphasized borders.
4. THE Design_Tokens SHALL define a primary text color of #2f3437 and a secondary text color of #6b6f76.
5. THE Design_Tokens SHALL define an accent color of #2383e2 used exclusively for focus rings and selection indicators.
6. THE Design_Tokens SHALL define shadows using soft, low-opacity values that produce no more than 2px visible spread for standard components, while allowing up to 4px spread for overlay components (modals, dropdowns) that require stronger visual hierarchy.
7. THE Design_Tokens SHALL define a font stack starting with Inter followed by the system font fallback chain.
8. THE Design_Tokens SHALL define transition durations between 120ms and 180ms with ease-out timing functions.
9. THE Design_Tokens SHALL remove all glassmorphism tokens (glass-bg, glass-blur, backdrop-filter values) and neon glow tokens.
10. THE Design_Tokens SHALL define a canvas background as white with a subtle dot grid pattern using a dot color no darker than rgba(0,0,0,0.06).
11. THE Design_Tokens SHALL map to React_Flow_CSS_Variables within the `.react-flow` scope, overriding `--xy-node-background-color-default`, `--xy-node-border-default`, `--xy-node-boxshadow-hover-default`, `--xy-node-boxshadow-selected-default`, `--xy-edge-stroke-default`, `--xy-edge-stroke-selected-default`, `--xy-handle-background-color-default`, `--xy-handle-border-color-default`, and `--xy-background-pattern-dots-color-default` with the corresponding Notion-style token values.
12. THE Design_Tokens SHALL override `--xy-selection-background-color-default` and `--xy-selection-border-default` within the `.react-flow` scope to use the accent color at low opacity for multi-select rectangles.

### Requirement 2: Node Component Visual Redesign

**User Story:** As a workflow designer, I want nodes to appear as clean, minimal cards with consistent styling, so that the canvas feels calm and readable like a Notion workspace.

#### Acceptance Criteria

1. THE Node_Component SHALL render with a border-radius between 10px and 12px.
2. THE Node_Component SHALL render with a soft shadow producing no visible glow effect.
3. THE Node_Component SHALL render with internal padding between 10px and 14px.
4. WHEN a user hovers over a node, THE Node_Component SHALL darken its border color subtly without adding glow or elevation change.
5. WHEN a node is selected, THE Node_Component SHALL display a thin blue outline (1.5px to 2px) using the accent color with no glow effect, layering this outline on top of React Flow's built-in selection styling rather than replacing it.
6. THE Node_Component SHALL use a uniform background color of #f7f7f5 for all state types instead of type-specific gradient backgrounds.
7. THE Node_Component SHALL differentiate state types through a small colored indicator dot or left-border accent rather than full background tinting.
8. THE Node_Component SHALL completely remove all neon glow animations and execution pulse effects before implementing replacement subtle opacity transitions; both animation types SHALL NOT coexist.
9. THE nodeTypes_Object SHALL be defined outside the React component that renders the React Flow canvas to prevent unnecessary re-renders on each parent render cycle.
10. WHEN a node is selected, THE Node_Component SHALL leverage React Flow's built-in `.react-flow__node.selected` CSS class for base selection state, layering the accent-color outline on top of the library's default selection mechanism.

### Requirement 3: Edge Visual Redesign

**User Story:** As a workflow designer, I want connection lines to be thin and subtle, so that the canvas remains visually calm and edges do not distract from node content.

#### Acceptance Criteria

1. THE Edge SHALL render as a thin line with a stroke width between 1px and 1.5px.
2. THE Edge SHALL render using a neutral grey color no darker than #d0d0d0 in its default state.
3. THE Edge SHALL use smooth bezier curves as the path type.
4. WHEN an edge is selected, THE Edge SHALL darken its stroke color to approximately #9b9b9b without adding glow or highlight effects.
5. THE Edge SHALL not display any neon, bright, or high-saturation colors in any interaction state.
6. THE Edge SHALL be implemented as a custom edge component that uses the BaseEdge component with the `getBezierPath` utility function for path calculation.
7. THE edgeTypes_Object SHALL be defined outside the React component that renders the React Flow canvas to prevent unnecessary re-renders on each parent render cycle.
8. THE Edge SHALL override `--xy-edge-stroke-default` to match the neutral grey color and `--xy-edge-stroke-selected-default` to match the darkened selection stroke color within the `.react-flow` scope.

### Requirement 4: Icon System Replacement

**User Story:** As a workflow designer, I want consistent, minimal line icons for state types, so that the interface feels polished and professional like Notion.

#### Acceptance Criteria

1. THE Icon_System SHALL use Lucide React icons (or equivalent simple line icon set) with a consistent stroke width of 1.5px.
2. THE Icon_System SHALL replace all emoji state-type icons in the Node_Component with line icons.
3. THE Icon_System SHALL render icons in the secondary text color (#6b6f76) by default and primary text color (#2f3437) when the parent element is hovered or selected.
4. THE Icon_System SHALL use unfilled (outline-only) icon variants by default, and SHALL automatically switch to filled variants when semantic clarity requires it.

### Requirement 5: Toolbar Visual Redesign

**User Story:** As a user, I want the toolbar to feel minimal and unobtrusive, so that it does not compete visually with the canvas workspace.

#### Acceptance Criteria

1. THE Toolbar SHALL render with a white (#ffffff) background and a single bottom border using the standard border color.
2. THE Toolbar SHALL remove all glassmorphism effects (backdrop-filter, translucent backgrounds).
3. THE Toolbar SHALL render buttons with a clean flat style: no gradients, no box-shadow in default state, and subtle border.
4. WHEN a toolbar button is hovered, THE Toolbar SHALL display a light grey background tint (#f7f7f5) without elevation changes, though subtle depth cues such as shadows or border changes are permitted as long as the button does not physically move or scale.
5. WHEN a toolbar button receives focus, THE Toolbar SHALL display a thin blue focus ring using the accent color (2px outline, 2px offset).
6. THE Toolbar SHALL render primary action buttons (Save, Execute) with the accent color as background and white text, without gradient or glow effects.

### Requirement 6: Canvas Workspace Styling

**User Story:** As a workflow designer, I want the canvas background to feel like a Notion workspace with generous whitespace, so that the editing experience is calm and focused.

#### Acceptance Criteria

1. THE Canvas SHALL render with a white background (#ffffff).
2. THE Canvas SHALL display a subtle dot-grid pattern with dots no larger than 1px and spaced at least 20px apart.
3. THE Canvas SHALL use a dot color no darker than rgba(0,0,0,0.06) for the grid pattern.
4. THE Canvas SHALL remove all mesh gradient overlays and dark background colors.
5. THE Canvas SHALL provide at least 16px of padding between the canvas edge and the nearest UI chrome element.
6. THE Canvas SHALL use React Flow's built-in `<Background />` component with `variant="dots"` prop for the dot-grid pattern rendering.
7. THE Canvas SHALL override `--xy-background-pattern-dots-color-default` within the `.react-flow` scope to enforce the dot color constraint of no darker than rgba(0,0,0,0.06).
8. THE Canvas SHALL override `--xy-controls-button-background-color-default`, `--xy-controls-button-background-color-hover-default`, and `--xy-controls-button-border-color-default` within the `.react-flow` scope to match the Notion-style flat button styling.

### Requirement 7: Animation and Transition Standards

**User Story:** As a user, I want interactions to feel instant but smooth, so that the interface responds naturally without distracting motion.

#### Acceptance Criteria

1. THE Design_Tokens SHALL define all hover and selection transitions with durations between 120ms and 180ms.
2. THE Design_Tokens SHALL use ease-out as the exclusive timing function for all UI transitions, prohibiting bounce, spring, elastic, or any other timing function.
3. WHEN a reduced-motion preference is detected, THE Node_Component SHALL disable all animations and transitions.
4. THE Node_Component SHALL not use any bounce, spring, or elastic timing functions.
5. THE Node_Component SHALL not use any animation with a duration exceeding 200ms for hover or selection state changes.

### Requirement 8: Scope Boundary — No Logic Changes

**User Story:** As a developer, I want assurance that this redesign touches only visual presentation, so that I can review changes with confidence that no behavior has changed.

#### Acceptance Criteria

1. THE Node_Component SHALL preserve all existing React props, event handlers, and data attributes without modification.
2. THE Node_Component SHALL preserve all Handle components (connection points) with their existing IDs, types, and positions.
3. THE Toolbar SHALL preserve all existing button onClick handlers, disabled states, and test-id attributes.
4. WHEN a file is modified as part of this redesign, THE modification SHALL be limited to CSS modules, token definitions, icon imports, and className assignments.

### Requirement 9: React Flow Integration Standards

**User Story:** As a developer, I want the redesign to follow React Flow's official theming patterns and best practices, so that styles integrate cleanly with the library rather than fighting its defaults, and future upgrades are straightforward.

#### Acceptance Criteria

1. THE Canvas SHALL import React Flow's base styles from `@xyflow/react/dist/base.css` as the foundation, applying all custom theme overrides on top of these base styles.
2. THE Canvas SHALL scope all CSS overrides within the `.react-flow` class to prevent style leakage to non-canvas elements.
3. THE nodeTypes_Object and edgeTypes_Object SHALL be defined as module-level constants outside any React component to prevent referential instability and re-mount cycles.
4. THE Node_Component SHALL rely on React Flow's built-in CSS state classes (`.react-flow__node.selected`, `.react-flow__handle`) for selection and handle styling, extending them rather than replacing them.
5. THE Edge SHALL use the BaseEdge component combined with React Flow's path utility functions (`getBezierPath`, `getSmoothStepPath`) rather than manually constructing SVG path elements.
6. THE Canvas SHALL support React Flow's `colorMode` prop to enable future dark mode support, ensuring that all CSS variable overrides are structured to allow per-mode values.
7. THE Design_Tokens SHALL organize React Flow CSS variable overrides in a dedicated section of `tokens.css` clearly separated from application-level tokens, scoped under `.react-flow`.
8. THE Edge SHALL use React Flow's built-in `.react-flow__edge` and `.react-flow__edge.selected` classes for all edge styling regardless of selection state, rather than custom class logic.
9. WHEN styling handles, THE Node_Component SHALL target `.react-flow__handle` within the node scope and override `--xy-handle-background-color-default` and `--xy-handle-border-color-default` for consistent Notion-style handle appearance.
10. THE Canvas SHALL override `--xy-controls-box-shadow-default` within the `.react-flow` scope to match the soft, low-opacity shadow standard defined in the Design_Tokens.
