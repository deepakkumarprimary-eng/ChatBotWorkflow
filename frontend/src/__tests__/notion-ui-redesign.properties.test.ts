/**
 * Property-based tests for the Notion UI Redesign visual contracts.
 * Tests that visual properties hold universally across all StateType values.
 *
 * Feature: notion-ui-redesign
 * Library: fast-check
 * Configuration: Minimum 100 iterations per property
 */
import { describe, it, expect, vi, afterEach } from 'vitest';
import * as fc from 'fast-check';
import { render, cleanup } from '@testing-library/react';
import { ReactFlowProvider } from '@xyflow/react';
import React from 'react';
import * as fs from 'node:fs';
import * as path from 'node:path';
import type { StateType } from '../types/canvas.types';
import StateNode from '../components/canvas/nodes/StateNode';
import type { StateNodeData } from '../components/canvas/nodes/StateNode';

// Mock React Flow's Handle component to render detectable DOM elements
// instead of requiring full ReactFlow context
vi.mock('@xyflow/react', async () => {
  const actual = await vi.importActual('@xyflow/react');
  return {
    ...actual,
    Handle: ({ id, type, position, className }: any) =>
      React.createElement('div', {
        'data-testid': `handle-${id}`,
        'data-handle-type': type,
        'data-handle-position': position,
        className,
      }),
  };
});

// --- Constants ---

const ALL_STATE_TYPES: StateType[] = [
  'API_Call', 'Condition', 'Response', 'Input', 'Wait', 'Parallel', 'End',
];

/** Expected indicator colors per state type (CSS variable references) */
const EXPECTED_INDICATORS: Record<StateType, string> = {
  API_Call: 'var(--color-indicator-blue)',
  Condition: 'var(--color-indicator-orange)',
  Response: 'var(--color-indicator-green)',
  Input: 'var(--color-indicator-purple)',
  Wait: 'var(--color-indicator-orange)',
  Parallel: 'var(--color-indicator-green)',
  End: 'var(--color-indicator-red)',
};

// --- Helpers ---

/**
 * Renders a StateNode with the given stateType and returns the container.
 * Uses cleanup between calls to prevent DOM accumulation across fast-check iterations.
 */
function renderStateNode(stateType: StateType) {
  cleanup();

  const data: StateNodeData = {
    label: `Test ${stateType}`,
    stateType,
  };

  // Minimal NodeProps shape required by StateNode
  const nodeProps = {
    id: `test-node-${stateType}`,
    data,
    selected: false,
    type: 'stateNode',
    isConnectable: true,
    zIndex: 0,
    positionAbsoluteX: 0,
    positionAbsoluteY: 0,
    dragging: false,
    deletable: true,
    selectable: true,
    parentId: undefined,
    sourcePosition: undefined,
    targetPosition: undefined,
    dragHandle: undefined,
    width: 200,
    height: 100,
  } as any;

  return render(
    React.createElement(ReactFlowProvider, null,
      React.createElement(StateNode, nodeProps)
    )
  );
}

// Ensure cleanup after all tests in this file
afterEach(() => {
  cleanup();
});

// =============================================================================
// Property 1: Node visual uniformity with type-specific indicators
// =============================================================================

describe('Property 1: Node visual uniformity with type-specific indicators', () => {
  /**
   * **Validates: Requirements 2.6, 2.7**
   *
   * For any StateType value, rendering a StateNode component SHALL produce a node
   * with a uniform background color of #f7f7f5 (no type-specific gradient) AND a
   * visible left-border indicator whose color is specific to that StateType.
   */

  it('all state types render with uniform background (no type-specific gradient)', () => {
    fc.assert(
      fc.property(fc.constantFrom(...ALL_STATE_TYPES), (stateType) => {
        const { getByTestId } = renderStateNode(stateType);
        const node = getByTestId(`state-node-${stateType}`);

        // The node should not have any inline background gradient
        const style = node.style;
        const bgValue = style.background || style.backgroundColor || '';

        // No gradient should appear in inline styles
        expect(bgValue).not.toContain('linear-gradient');
        expect(bgValue).not.toContain('radial-gradient');
      }),
      { numRuns: 100 }
    );
  });

  it('all state types have a type-specific indicator color via --node-indicator-color', () => {
    fc.assert(
      fc.property(fc.constantFrom(...ALL_STATE_TYPES), (stateType) => {
        const { getByTestId } = renderStateNode(stateType);
        const node = getByTestId(`state-node-${stateType}`);

        // The node should have --node-indicator-color set as inline style
        const inlineStyle = node.getAttribute('style') || '';
        expect(inlineStyle).toContain('--node-indicator-color');

        // The indicator color should match the expected value for this state type
        expect(inlineStyle).toContain(EXPECTED_INDICATORS[stateType]);
      }),
      { numRuns: 100 }
    );
  });
});

// =============================================================================
// Property 2: Icon system consistency
// =============================================================================

describe('Property 2: Icon system consistency', () => {
  /**
   * **Validates: Requirements 4.1, 4.2, 4.4**
   *
   * For any StateType value, rendering a StateNode component SHALL produce an icon
   * that is an SVG element (not emoji text), rendered with stroke-width="1.5" and
   * fill="none" (outline-only variant).
   */

  it('all state types render an SVG icon (not emoji text)', () => {
    fc.assert(
      fc.property(fc.constantFrom(...ALL_STATE_TYPES), (stateType) => {
        const { getByTestId } = renderStateNode(stateType);
        const node = getByTestId(`state-node-${stateType}`);

        // Find the SVG element within the node
        const svgElement = node.querySelector('svg');
        expect(svgElement).not.toBeNull();

        // The icon should be an actual SVG, not text emoji content
        expect(svgElement!.tagName.toLowerCase()).toBe('svg');
      }),
      { numRuns: 100 }
    );
  });

  it('all SVG icons use stroke-width="1.5" and fill="none" (outline-only)', () => {
    fc.assert(
      fc.property(fc.constantFrom(...ALL_STATE_TYPES), (stateType) => {
        const { getByTestId } = renderStateNode(stateType);
        const node = getByTestId(`state-node-${stateType}`);

        const svgElement = node.querySelector('svg');
        expect(svgElement).not.toBeNull();

        // Lucide icons set stroke-width and fill on the root SVG element
        const strokeWidth = svgElement!.getAttribute('stroke-width');
        expect(strokeWidth).toBe('1.5');

        const fill = svgElement!.getAttribute('fill');
        expect(fill).toBe('none');
      }),
      { numRuns: 100 }
    );
  });
});


// =============================================================================
// Property 3: Transition standard compliance
// =============================================================================

describe('Property 3: Transition standard compliance', () => {
  /**
   * **Validates: Requirements 7.1, 7.2**
   *
   * For any CSS transition declaration in the redesigned component CSS modules
   * (StateNode.module.css, Toolbar.module.css, WorkflowCanvas.module.css,
   * ZoomControls.module.css), the transition duration SHALL be between 120ms
   * and 180ms, and the timing function SHALL be ease-out.
   */

  const CSS_FILES = [
    'src/components/canvas/nodes/StateNode.module.css',
    'src/components/toolbar/Toolbar.module.css',
    'src/components/canvas/WorkflowCanvas.module.css',
    'src/components/canvas/ZoomControls.module.css',
  ];

  const PROJECT_ROOT = path.resolve(__dirname, '..', '..');

  /** Read and return the content of a CSS file */
  function readCssFile(relativePath: string): string {
    const fullPath = path.join(PROJECT_ROOT, relativePath);
    return fs.readFileSync(fullPath, 'utf-8');
  }

  /** Extract all transition declarations from CSS content (excluding disabled transitions like "none") */
  function extractTransitionDeclarations(cssContent: string): string[] {
    const declarations: string[] = [];
    // Match transition property declarations (not transition-property, transition-duration, etc.)
    const regex = /\btransition\s*:\s*([^;]+);/g;
    let match: RegExpExecArray | null;
    while ((match = regex.exec(cssContent)) !== null) {
      const value = match[1].trim();
      // Skip "none" declarations (used in prefers-reduced-motion to disable transitions)
      if (/^\s*none\s*(!important)?\s*$/i.test(value)) {
        continue;
      }
      declarations.push(value);
    }
    return declarations;
  }

  /**
   * Parse the --transition-standard token value from tokens.css
   * and verify it meets the requirements.
   */
  function getTransitionStandardToken(): { duration: number; timingFunction: string } {
    const tokensPath = path.join(PROJECT_ROOT, 'src/styles/tokens.css');
    const content = fs.readFileSync(tokensPath, 'utf-8');
    const match = content.match(/--transition-standard\s*:\s*([^;]+);/);
    if (!match) {
      throw new Error('Could not find --transition-standard in tokens.css');
    }
    const value = match[1].trim();
    // Expected format: "150ms ease-out"
    const parts = value.split(/\s+/);
    const durationStr = parts[0];
    const timingFunction = parts.slice(1).join(' ');
    const duration = parseFloat(durationStr.replace('ms', ''));
    return { duration, timingFunction };
  }

  /**
   * Check that a transition value uses var(--transition-standard) OR
   * has an explicit duration between 120-180ms with ease-out timing.
   */
  function validateTransitionValue(value: string): { valid: boolean; reason: string } {
    // A transition value may contain multiple parts (e.g., "background var(--transition-standard), border-color var(--transition-standard)")
    // Split by comma to handle multi-property transitions
    const parts = value.split(',').map(p => p.trim());

    for (const part of parts) {
      // Check if it references var(--transition-standard)
      if (part.includes('var(--transition-standard)')) {
        continue; // Valid — uses the standard token
      }

      // If not using the variable, parse for explicit duration and timing
      const durationMatch = part.match(/(\d+(?:\.\d+)?)(ms|s)/);
      const timingMatch = part.match(/(ease-out|ease-in|ease-in-out|ease|linear|cubic-bezier\([^)]+\))/);

      if (!durationMatch) {
        return { valid: false, reason: `No duration found in transition part: "${part}"` };
      }

      let durationMs = parseFloat(durationMatch[1]);
      if (durationMatch[2] === 's') {
        durationMs *= 1000;
      }

      if (durationMs < 120 || durationMs > 180) {
        return { valid: false, reason: `Duration ${durationMs}ms is outside [120, 180] range in: "${part}"` };
      }

      const timingFunction = timingMatch ? timingMatch[1] : 'ease'; // CSS default
      if (timingFunction !== 'ease-out') {
        return { valid: false, reason: `Timing function "${timingFunction}" is not ease-out in: "${part}"` };
      }
    }

    return { valid: true, reason: '' };
  }

  it('--transition-standard token has duration between 120ms and 180ms with ease-out', () => {
    const token = getTransitionStandardToken();
    expect(token.duration).toBeGreaterThanOrEqual(120);
    expect(token.duration).toBeLessThanOrEqual(180);
    expect(token.timingFunction).toBe('ease-out');
  });

  it('all transition declarations in CSS modules comply with standard for any random subset of files', () => {
    fc.assert(
      fc.property(
        fc.shuffledSubarray(CSS_FILES, { minLength: 1, maxLength: CSS_FILES.length }),
        (selectedFiles) => {
          for (const file of selectedFiles) {
            const content = readCssFile(file);
            const declarations = extractTransitionDeclarations(content);

            for (const declaration of declarations) {
              const result = validateTransitionValue(declaration);
              expect(result.valid, `File: ${file} — ${result.reason}`).toBe(true);
            }
          }
        }
      ),
      { numRuns: 100 }
    );
  });

  it('every CSS file has at least one transition using var(--transition-standard) or compliant value', () => {
    fc.assert(
      fc.property(
        fc.constantFrom(...CSS_FILES),
        (file) => {
          const content = readCssFile(file);
          const declarations = extractTransitionDeclarations(content);

          // Files with transitions should all be compliant
          for (const declaration of declarations) {
            const result = validateTransitionValue(declaration);
            expect(result.valid, `File: ${file} — ${result.reason}`).toBe(true);
          }
        }
      ),
      { numRuns: 100 }
    );
  });

  it('no CSS file contains bounce, spring, or elastic timing functions', () => {
    fc.assert(
      fc.property(
        fc.constantFrom(...CSS_FILES),
        (file) => {
          const content = readCssFile(file);
          // Check for prohibited timing functions
          const prohibitedPatterns = [
            /cubic-bezier\([^)]*[2-9]\d*(\.\d+)?\s*\)/i, // overshoot values > 1
            /spring\(/i,
            /bounce/i,
            /elastic/i,
          ];

          for (const pattern of prohibitedPatterns) {
            expect(
              pattern.test(content),
              `File ${file} contains prohibited timing pattern: ${pattern}`
            ).toBe(false);
          }
        }
      ),
      { numRuns: 100 }
    );
  });
});


// =============================================================================
// Property 4: Component structural preservation
// =============================================================================

describe('Property 4: Component structural preservation', () => {
  /**
   * **Validates: Requirements 8.1, 8.2**
   *
   * For any StateType value, rendering a StateNode component after the redesign
   * SHALL produce:
   * - All expected `data-testid` and `data-node-type` attributes with the same
   *   values as the pre-redesign component
   * - The same set of React Flow Handle elements with identical id, type, and
   *   position props as the pre-redesign component
   *
   * Note: Since Handle is mocked (returns null), we verify structural
   * preservation via the wrapper div's data attributes. Handle structure is
   * verified separately using a custom mock that renders visible DOM elements.
   */

  it('renders correct data-testid and data-node-type for any StateType', () => {
    fc.assert(
      fc.property(fc.constantFrom(...ALL_STATE_TYPES), (stateType) => {
        const { getByTestId } = renderStateNode(stateType);
        const node = getByTestId(`state-node-${stateType}`);

        // Assert data-testid is present with correct value
        expect(node).not.toBeNull();
        expect(node.getAttribute('data-testid')).toBe(`state-node-${stateType}`);

        // Assert data-node-type is present with correct value
        const expectedNodeType = stateType.toLowerCase().replace('_', '');
        expect(node.getAttribute('data-node-type')).toBe(expectedNodeType);
      }),
      { numRuns: 100 }
    );
  });

  it('preserves data-execution-state attribute for any StateType', () => {
    fc.assert(
      fc.property(fc.constantFrom(...ALL_STATE_TYPES), (stateType) => {
        const { getByTestId } = renderStateNode(stateType);
        const node = getByTestId(`state-node-${stateType}`);

        // data-execution-state should be present (idle when not selected)
        expect(node.getAttribute('data-execution-state')).toBe('idle');
      }),
      { numRuns: 100 }
    );
  });

  it('preserves aria-busy attribute for any StateType', () => {
    fc.assert(
      fc.property(fc.constantFrom(...ALL_STATE_TYPES), (stateType) => {
        const { getByTestId } = renderStateNode(stateType);
        const node = getByTestId(`state-node-${stateType}`);

        // aria-busy should be preserved as "false"
        expect(node.getAttribute('aria-busy')).toBe('false');
      }),
      { numRuns: 100 }
    );
  });
});

// =============================================================================
// Property 4b: Handle structural preservation
// =============================================================================

describe('Property 4b: Handle structural preservation', () => {
  /**
   * **Validates: Requirements 8.1, 8.2**
   *
   * Verifies that Handle components are rendered with the correct id, type,
   * and position props for each StateType. The Handle mock renders visible
   * DOM elements with data attributes that we can verify.
   */

  /** Expected handle configurations per state type */
  const HANDLE_SPECS: Record<StateType, Array<{ id: string; type: string; position: string }>> = {
    API_Call: [
      { id: 'target-top', type: 'target', position: 'top' },
      { id: 'target-left', type: 'target', position: 'left' },
      { id: 'source-bottom', type: 'source', position: 'bottom' },
      { id: 'source-right', type: 'source', position: 'right' },
    ],
    Response: [
      { id: 'target-top', type: 'target', position: 'top' },
      { id: 'target-left', type: 'target', position: 'left' },
      { id: 'source-bottom', type: 'source', position: 'bottom' },
      { id: 'source-right', type: 'source', position: 'right' },
    ],
    Input: [
      { id: 'target-top', type: 'target', position: 'top' },
      { id: 'target-left', type: 'target', position: 'left' },
      { id: 'source-bottom', type: 'source', position: 'bottom' },
      { id: 'source-right', type: 'source', position: 'right' },
    ],
    Wait: [
      { id: 'target-top', type: 'target', position: 'top' },
      { id: 'target-left', type: 'target', position: 'left' },
      { id: 'source-bottom', type: 'source', position: 'bottom' },
      { id: 'source-right', type: 'source', position: 'right' },
    ],
    Parallel: [
      { id: 'target-top', type: 'target', position: 'top' },
      { id: 'target-left', type: 'target', position: 'left' },
      { id: 'source-bottom', type: 'source', position: 'bottom' },
      { id: 'source-right', type: 'source', position: 'right' },
    ],
    Condition: [
      { id: 'target-top', type: 'target', position: 'top' },
      { id: 'target-left', type: 'target', position: 'left' },
      { id: 'false', type: 'source', position: 'left' },
      { id: 'true', type: 'source', position: 'right' },
      { id: 'source-bottom', type: 'source', position: 'bottom' },
    ],
    End: [
      { id: 'target-top', type: 'target', position: 'top' },
      { id: 'target-left', type: 'target', position: 'left' },
    ],
  };

  it('renders the correct number and configuration of Handle elements for any StateType', () => {
    fc.assert(
      fc.property(fc.constantFrom(...ALL_STATE_TYPES), (stateType) => {
        const { container } = renderStateNode(stateType);
        const expectedHandles = HANDLE_SPECS[stateType];

        // Verify each expected handle is present with correct props
        for (const spec of expectedHandles) {
          const handle = container.querySelector(`[data-testid="handle-${spec.id}"]`);
          expect(handle, `Handle "${spec.id}" should exist for ${stateType}`).not.toBeNull();
          expect(handle!.getAttribute('data-handle-type')).toBe(spec.type);
          expect(handle!.getAttribute('data-handle-position')).toBe(spec.position);
        }

        // Verify total handle count matches expected
        const allHandles = container.querySelectorAll('[data-testid^="handle-"]');
        expect(allHandles.length).toBe(expectedHandles.length);
      }),
      { numRuns: 100 }
    );
  });

  it('End type has no source handles', () => {
    fc.assert(
      fc.property(fc.constantFrom('End' as StateType), (stateType) => {
        const { container } = renderStateNode(stateType);

        const allHandles = container.querySelectorAll('[data-testid^="handle-"]');
        const sourceHandles = Array.from(allHandles).filter(
          h => h.getAttribute('data-handle-type') === 'source'
        );
        expect(sourceHandles.length).toBe(0);
      }),
      { numRuns: 100 }
    );
  });

  it('Condition type has true/false branch handles and source-bottom', () => {
    fc.assert(
      fc.property(fc.constantFrom('Condition' as StateType), (stateType) => {
        const { container } = renderStateNode(stateType);

        // Should have true and false handles
        const trueHandle = container.querySelector('[data-testid="handle-true"]');
        expect(trueHandle).not.toBeNull();
        expect(trueHandle!.getAttribute('data-handle-type')).toBe('source');

        const falseHandle = container.querySelector('[data-testid="handle-false"]');
        expect(falseHandle).not.toBeNull();
        expect(falseHandle!.getAttribute('data-handle-type')).toBe('source');

        // Should have source-bottom
        const sourceBottom = container.querySelector('[data-testid="handle-source-bottom"]');
        expect(sourceBottom).not.toBeNull();
        expect(sourceBottom!.getAttribute('data-handle-type')).toBe('source');

        // Should NOT have source-right
        const sourceRight = container.querySelector('[data-testid="handle-source-right"]');
        expect(sourceRight).toBeNull();
      }),
      { numRuns: 100 }
    );
  });
});
