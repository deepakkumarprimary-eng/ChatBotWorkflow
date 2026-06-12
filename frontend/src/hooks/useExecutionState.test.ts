/**
 * Unit tests for useExecutionState hook.
 * Tests state transitions, API interaction, and error handling.
 * Requirements: 7.4
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useExecutionState } from './useExecutionState';

vi.mock('../services/workflowApi', () => ({
  executeWorkflow: vi.fn(),
}));

import { executeWorkflow } from '../services/workflowApi';

const mockedExecuteWorkflow = vi.mocked(executeWorkflow);

describe('useExecutionState', () => {
  let showToast: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    vi.clearAllMocks();
    showToast = vi.fn();
  });

  it('should have correct initial state', () => {
    const { result } = renderHook(() =>
      useExecutionState({ currentWorkflowId: null, showToast })
    );

    expect(result.current.activeExecutionId).toBeNull();
    expect(result.current.executionListVisible).toBe(false);
  });

  it('should call API and set activeExecutionId on successful execute', async () => {
    mockedExecuteWorkflow.mockResolvedValue({ executionId: 'exec-123' });

    const { result } = renderHook(() =>
      useExecutionState({ currentWorkflowId: 'wf-1', showToast })
    );

    await act(async () => {
      await result.current.handleExecute();
    });

    expect(mockedExecuteWorkflow).toHaveBeenCalledWith('wf-1');
    expect(result.current.activeExecutionId).toBe('exec-123');
    expect(showToast).toHaveBeenCalledWith('Workflow execution started', 'success');
  });

  it('should not call API when currentWorkflowId is null', async () => {
    const { result } = renderHook(() =>
      useExecutionState({ currentWorkflowId: null, showToast })
    );

    await act(async () => {
      await result.current.handleExecute();
    });

    expect(mockedExecuteWorkflow).not.toHaveBeenCalled();
    expect(result.current.activeExecutionId).toBeNull();
    expect(showToast).not.toHaveBeenCalled();
  });

  it('should call showToast with error message on API failure', async () => {
    mockedExecuteWorkflow.mockRejectedValue(new Error('Network error'));

    const { result } = renderHook(() =>
      useExecutionState({ currentWorkflowId: 'wf-1', showToast })
    );

    await act(async () => {
      await result.current.handleExecute();
    });

    expect(result.current.activeExecutionId).toBeNull();
    expect(showToast).toHaveBeenCalledWith('Network error');
  });

  it('should show generic error message for non-Error exceptions', async () => {
    mockedExecuteWorkflow.mockRejectedValue('some string error');

    const { result } = renderHook(() =>
      useExecutionState({ currentWorkflowId: 'wf-1', showToast })
    );

    await act(async () => {
      await result.current.handleExecute();
    });

    expect(showToast).toHaveBeenCalledWith('Failed to execute workflow');
  });

  it('should update activeExecutionId via setActiveExecutionId', () => {
    const { result } = renderHook(() =>
      useExecutionState({ currentWorkflowId: 'wf-1', showToast })
    );

    act(() => {
      result.current.setActiveExecutionId('exec-456');
    });

    expect(result.current.activeExecutionId).toBe('exec-456');
  });

  it('should toggle executionListVisible', () => {
    const { result } = renderHook(() =>
      useExecutionState({ currentWorkflowId: 'wf-1', showToast })
    );

    expect(result.current.executionListVisible).toBe(false);

    act(() => {
      result.current.setExecutionListVisible(true);
    });

    expect(result.current.executionListVisible).toBe(true);

    act(() => {
      result.current.setExecutionListVisible(false);
    });

    expect(result.current.executionListVisible).toBe(false);
  });
});
