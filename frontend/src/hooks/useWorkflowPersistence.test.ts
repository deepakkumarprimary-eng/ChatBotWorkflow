/**
 * Unit tests for useWorkflowPersistence hook.
 * Tests initial state, new workflow, save flow, and error handling.
 * Requirements: 7.4
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useWorkflowPersistence } from './useWorkflowPersistence';
import type { UseWorkflowPersistenceOptions } from './useWorkflowPersistence';
import type { CanvasState } from '../types/canvas.types';

vi.mock('../services/workflowApi', () => ({
  saveWorkflow: vi.fn(),
  updateWorkflow: vi.fn(),
  loadWorkflow: vi.fn(),
}));

vi.mock('../services/workflowExportImport', () => ({
  exportWorkflow: vi.fn(),
  importWorkflow: vi.fn(),
}));

import { saveWorkflow, updateWorkflow } from '../services/workflowApi';

const mockedSaveWorkflow = vi.mocked(saveWorkflow);
const mockedUpdateWorkflow = vi.mocked(updateWorkflow);

function createMockCanvasState(): CanvasState {
  return {
    states: new Map(),
    transitions: new Map(),
    selectedStateId: null,
    zoom: 1,
    panOffset: { x: 0, y: 0 },
    undoStack: [],
    redoStack: [],
    contextVariables: [],
  };
}

function createDefaultOptions(overrides?: Partial<UseWorkflowPersistenceOptions>): UseWorkflowPersistenceOptions {
  return {
    canvasState: createMockCanvasState(),
    showToast: vi.fn(),
    setInitialStates: vi.fn(),
    setInitialTransitions: vi.fn(),
    setInitialContextVars: vi.fn(),
    setContextVariables: vi.fn(),
    setCanvasKey: vi.fn(),
    setSelectedState: vi.fn(),
    ...overrides,
  };
}

describe('useWorkflowPersistence', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('initial state', () => {
    it('should have null workflowId and empty name/description', () => {
      const options = createDefaultOptions();
      const { result } = renderHook(() => useWorkflowPersistence(options));

      expect(result.current.currentWorkflowId).toBeNull();
      expect(result.current.currentWorkflowName).toBe('');
      expect(result.current.currentWorkflowDescription).toBe('');
    });

    it('should have dialogs not visible', () => {
      const options = createDefaultOptions();
      const { result } = renderHook(() => useWorkflowPersistence(options));

      expect(result.current.saveDialogVisible).toBe(false);
      expect(result.current.listPanelVisible).toBe(false);
    });
  });

  describe('handleNewWorkflow', () => {
    it('should reset all workflow state', () => {
      const options = createDefaultOptions();
      const { result } = renderHook(() => useWorkflowPersistence(options));

      act(() => {
        result.current.handleNewWorkflow();
      });

      expect(result.current.currentWorkflowId).toBeNull();
      expect(result.current.currentWorkflowName).toBe('');
      expect(result.current.currentWorkflowDescription).toBe('');
      expect(options.setInitialStates).toHaveBeenCalledWith([]);
      expect(options.setInitialTransitions).toHaveBeenCalledWith([]);
      expect(options.setInitialContextVars).toHaveBeenCalledWith([]);
      expect(options.setContextVariables).toHaveBeenCalledWith([]);
      expect(options.setCanvasKey).toHaveBeenCalled();
      expect(options.setSelectedState).toHaveBeenCalledWith(null);
    });
  });

  describe('handleSaveClick', () => {
    it('should show save dialog when no workflowId exists', () => {
      const options = createDefaultOptions();
      const { result } = renderHook(() => useWorkflowPersistence(options));

      act(() => {
        result.current.handleSaveClick();
      });

      expect(result.current.saveDialogVisible).toBe(true);
    });

    it('should call update directly when workflowId exists', async () => {
      mockedUpdateWorkflow.mockResolvedValue({
        id: 'wf-1',
        name: 'My Workflow',
        description: 'desc',
        definition: { metadata: {} as any, states: [], transitions: [], contextVariables: [] },
        currentVersion: 1,
        createdAt: '',
        lastModifiedAt: '',
      });

      const options = createDefaultOptions();
      const { result } = renderHook(() => useWorkflowPersistence(options));

      // First, save to set a workflowId
      mockedSaveWorkflow.mockResolvedValue({
        id: 'wf-1',
        name: 'My Workflow',
        description: 'desc',
        definition: { metadata: {} as any, states: [], transitions: [], contextVariables: [] },
        currentVersion: 1,
        createdAt: '',
        lastModifiedAt: '',
      });

      await act(async () => {
        await result.current.handleSaveWorkflow('My Workflow', 'desc');
      });

      // Now handleSaveClick should call update directly (not show dialog)
      await act(async () => {
        result.current.handleSaveClick();
      });

      expect(result.current.saveDialogVisible).toBe(false);
      expect(mockedUpdateWorkflow).toHaveBeenCalledWith('wf-1', 'My Workflow', 'desc', options.canvasState);
    });

    it('should not do anything when canvasState is null', () => {
      const options = createDefaultOptions({ canvasState: null });
      const { result } = renderHook(() => useWorkflowPersistence(options));

      act(() => {
        result.current.handleSaveClick();
      });

      expect(result.current.saveDialogVisible).toBe(false);
    });
  });

  describe('handleSaveWorkflow', () => {
    it('should create a new workflow when no workflowId exists', async () => {
      mockedSaveWorkflow.mockResolvedValue({
        id: 'new-wf-id',
        name: 'Test WF',
        description: 'A workflow',
        definition: { metadata: {} as any, states: [], transitions: [], contextVariables: [] },
        currentVersion: 1,
        createdAt: '',
        lastModifiedAt: '',
      });

      const showToast = vi.fn();
      const options = createDefaultOptions({ showToast });
      const { result } = renderHook(() => useWorkflowPersistence(options));

      await act(async () => {
        await result.current.handleSaveWorkflow('Test WF', 'A workflow');
      });

      expect(mockedSaveWorkflow).toHaveBeenCalledWith('Test WF', 'A workflow', options.canvasState);
      expect(result.current.currentWorkflowId).toBe('new-wf-id');
      expect(result.current.currentWorkflowName).toBe('Test WF');
      expect(result.current.currentWorkflowDescription).toBe('A workflow');
      expect(showToast).toHaveBeenCalledWith('Workflow created successfully', 'success');
    });

    it('should show error toast on save failure', async () => {
      mockedSaveWorkflow.mockRejectedValue(new Error('Server error'));

      const showToast = vi.fn();
      const options = createDefaultOptions({ showToast });
      const { result } = renderHook(() => useWorkflowPersistence(options));

      await act(async () => {
        await result.current.handleSaveWorkflow('Test', 'desc');
      });

      expect(showToast).toHaveBeenCalledWith('Server error');
      expect(result.current.currentWorkflowId).toBeNull();
    });

    it('should show generic error for non-Error exceptions', async () => {
      mockedSaveWorkflow.mockRejectedValue('unknown');

      const showToast = vi.fn();
      const options = createDefaultOptions({ showToast });
      const { result } = renderHook(() => useWorkflowPersistence(options));

      await act(async () => {
        await result.current.handleSaveWorkflow('Test', 'desc');
      });

      expect(showToast).toHaveBeenCalledWith('Failed to save workflow');
    });
  });

  describe('dialog visibility', () => {
    it('should toggle saveDialogVisible', () => {
      const options = createDefaultOptions();
      const { result } = renderHook(() => useWorkflowPersistence(options));

      act(() => {
        result.current.setSaveDialogVisible(true);
      });
      expect(result.current.saveDialogVisible).toBe(true);

      act(() => {
        result.current.setSaveDialogVisible(false);
      });
      expect(result.current.saveDialogVisible).toBe(false);
    });

    it('should toggle listPanelVisible', () => {
      const options = createDefaultOptions();
      const { result } = renderHook(() => useWorkflowPersistence(options));

      act(() => {
        result.current.setListPanelVisible(true);
      });
      expect(result.current.listPanelVisible).toBe(true);

      act(() => {
        result.current.setListPanelVisible(false);
      });
      expect(result.current.listPanelVisible).toBe(false);
    });
  });
});
