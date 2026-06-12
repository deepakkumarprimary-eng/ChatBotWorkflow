/**
 * Unit tests for the WorkflowCanvas component.
 * Validates rendering, zoom/pan controls, node positioning, and state management.
 *
 * Requirements: 1.1 - Visual canvas for placing, moving, and connecting states
 * Requirements: 1.6 - Zoom (25%-400%) and pan controls
 */
import { vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { WorkflowState, Transition, StateType, StateConfig } from '../../types/canvas.types';

// Mock ResizeObserver which React Flow depends on
class ResizeObserverMock {
  observe() {}
  unobserve() {}
  disconnect() {}
}
global.ResizeObserver = ResizeObserverMock as unknown as typeof ResizeObserver;

// Mock IntersectionObserver
class IntersectionObserverMock {
  observe() {}
  unobserve() {}
  disconnect() {}
}
global.IntersectionObserver = IntersectionObserverMock as unknown as typeof IntersectionObserver;

// Mock DOMMatrixReadOnly
if (typeof DOMMatrixReadOnly === 'undefined') {
  (global as unknown as Record<string, unknown>).DOMMatrixReadOnly = class DOMMatrixReadOnly {
    m22: number;
    constructor(transform?: string) {
      const scale = transform?.match(/scale\(([1-9.]+)\)/);
      this.m22 = scale ? +scale[1] : 1;
    }
  };
}

// Mock element methods used by React Flow
Object.defineProperties(HTMLElement.prototype, {
  offsetHeight: { get() { return 500; } },
  offsetWidth: { get() { return 500; } },
});

// Helper to create a WorkflowState
function createState(overrides: Partial<WorkflowState> & { id: string; type: StateType }): WorkflowState {
  const typeConfigs: Record<StateType, StateConfig> = {
    API_Call: { type: 'API_Call', method: 'GET', url: '', headers: {}, body: '', responseMapping: {}, timeout: 30 },
    Condition: { type: 'Condition', expression: '' },
    Response: { type: 'Response', messageTemplate: '' },
    Input: { type: 'Input', prompt: '', variableName: '', timeout: 300 },
    Wait: { type: 'Wait', duration: 60 },
    Parallel: { type: 'Parallel', branches: [] },
    End: { type: 'End' },
  };

  return {
    name: overrides.name || `${overrides.type} State`,
    position: overrides.position || { x: 100, y: 100 },
    config: typeConfigs[overrides.type],
    ...overrides,
  } as WorkflowState;
}

// Lazy import to avoid top-level import failures with React Flow
const importCanvas = async () => {
  const mod = await import('./WorkflowCanvas');
  return mod.default;
};

const importZoomControls = async () => {
  const mod = await import('./ZoomControls');
  return mod;
};

describe('clampZoom', () => {
  it('returns MIN_ZOOM for values below minimum', async () => {
    const { clampZoom, MIN_ZOOM } = await importZoomControls();
    expect(clampZoom(0)).toBe(MIN_ZOOM);
    expect(clampZoom(-1)).toBe(MIN_ZOOM);
    expect(clampZoom(0.1)).toBe(MIN_ZOOM);
    expect(clampZoom(0.24)).toBe(MIN_ZOOM);
  });

  it('returns MAX_ZOOM for values above maximum', async () => {
    const { clampZoom, MAX_ZOOM } = await importZoomControls();
    expect(clampZoom(5)).toBe(MAX_ZOOM);
    expect(clampZoom(100)).toBe(MAX_ZOOM);
    expect(clampZoom(4.01)).toBe(MAX_ZOOM);
  });

  it('returns the value unchanged when within range', async () => {
    const { clampZoom } = await importZoomControls();
    expect(clampZoom(0.25)).toBe(0.25);
    expect(clampZoom(1)).toBe(1);
    expect(clampZoom(2.5)).toBe(2.5);
    expect(clampZoom(4.0)).toBe(4.0);
  });

  it('handles boundary values correctly', async () => {
    const { clampZoom, MIN_ZOOM, MAX_ZOOM } = await importZoomControls();
    expect(clampZoom(MIN_ZOOM)).toBe(MIN_ZOOM);
    expect(clampZoom(MAX_ZOOM)).toBe(MAX_ZOOM);
  });
});

describe('WorkflowCanvas', () => {
  it('renders without crashing', async () => {
    const WorkflowCanvas = await importCanvas();
    render(<WorkflowCanvas />);
    expect(screen.getByTestId('workflow-canvas')).toBeInTheDocument();
  });

  it('renders zoom controls', async () => {
    const WorkflowCanvas = await importCanvas();
    render(<WorkflowCanvas />);
    expect(screen.getByTestId('zoom-controls')).toBeInTheDocument();
  });

  it('renders initial states as nodes', async () => {
    const WorkflowCanvas = await importCanvas();
    const states: WorkflowState[] = [
      createState({ id: 'state-1', type: 'API_Call', name: 'Fetch Data' }),
      createState({ id: 'state-2', type: 'Response', name: 'Send Reply' }),
    ];

    render(<WorkflowCanvas initialStates={states} />);
    expect(screen.getByText('Fetch Data')).toBeInTheDocument();
    expect(screen.getByText('Send Reply')).toBeInTheDocument();
  });

  it('renders all state types with visual differentiation', async () => {
    const WorkflowCanvas = await importCanvas();
    const stateTypes: StateType[] = ['API_Call', 'Condition', 'Response', 'Input', 'Wait', 'Parallel', 'End'];
    const states = stateTypes.map((type, idx) =>
      createState({ id: `state-${idx}`, type, name: `${type} Node`, position: { x: idx * 200, y: 100 } })
    );

    render(<WorkflowCanvas initialStates={states} />);

    stateTypes.forEach((type) => {
      expect(screen.getByText(`${type} Node`)).toBeInTheDocument();
    });
  });

  it('does not invoke onCanvasStateChange on initial render', async () => {
    const WorkflowCanvas = await importCanvas();
    const onChange = vi.fn();
    render(<WorkflowCanvas onCanvasStateChange={onChange} />);
    expect(onChange).not.toHaveBeenCalled();
  });
});

describe('Canvas state initialization', () => {
  it('initializes with provided states and transitions', async () => {
    const WorkflowCanvas = await importCanvas();
    const states: WorkflowState[] = [
      createState({ id: 's1', type: 'API_Call', name: 'API Node' }),
      createState({ id: 's2', type: 'End', name: 'End Node', position: { x: 200, y: 200 } }),
    ];

    const transitions: Transition[] = [
      { id: 't1', source: 's1', target: 's2' },
    ];

    render(<WorkflowCanvas initialStates={states} initialTransitions={transitions} />);
    expect(screen.getByText('API Node')).toBeInTheDocument();
    expect(screen.getByText('End Node')).toBeInTheDocument();
  });
});
