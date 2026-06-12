/**
 * Custom hook encapsulating execution monitoring state and actions.
 * Manages the active execution ID, execution list visibility, and the execute action.
 *
 * Requirements: 7.2, 7.3
 */

import { useState, useCallback } from 'react';
import { executeWorkflow } from '../services/workflowApi';

// --- Options interface ---

export interface UseExecutionStateOptions {
  currentWorkflowId: string | null;
  showToast: (message: string, type?: 'success' | 'error') => void;
}

// --- Return type ---

export interface UseExecutionStateReturn {
  activeExecutionId: string | null;
  executionListVisible: boolean;
  handleExecute: () => Promise<void>;
  setActiveExecutionId: (id: string | null) => void;
  setExecutionListVisible: (visible: boolean) => void;
}

// --- Hook implementation ---

export function useExecutionState(
  options: UseExecutionStateOptions
): UseExecutionStateReturn {
  const { currentWorkflowId, showToast } = options;

  const [activeExecutionId, setActiveExecutionId] = useState<string | null>(null);
  const [executionListVisible, setExecutionListVisible] = useState(false);

  const handleExecute = useCallback(async () => {
    if (!currentWorkflowId) return;
    try {
      const { executionId } = await executeWorkflow(currentWorkflowId);
      setActiveExecutionId(executionId);
      showToast('Workflow execution started', 'success');
    } catch (err: unknown) {
      const message =
        err instanceof Error ? err.message : 'Failed to execute workflow';
      showToast(message);
    }
  }, [currentWorkflowId, showToast]);

  return {
    activeExecutionId,
    executionListVisible,
    handleExecute,
    setActiveExecutionId,
    setExecutionListVisible,
  };
}
