/**
 * Custom hook encapsulating workflow persistence state and actions.
 * Manages save, open, new, export, and import operations for workflows.
 *
 * Requirements: 7.1, 7.3, 7.5
 */

import { useState, useCallback, useRef } from 'react';
import type { CanvasState, WorkflowState, Transition, ContextVariable } from '../types/canvas.types';
import { saveWorkflow, updateWorkflow, loadWorkflow } from '../services/workflowApi';
import { exportWorkflow, importWorkflow } from '../services/workflowExportImport';

// --- Options interface ---

export interface UseWorkflowPersistenceOptions {
  canvasState: CanvasState | null;
  showToast: (message: string, type?: 'success' | 'error') => void;
  setInitialStates: (states: WorkflowState[]) => void;
  setInitialTransitions: (transitions: Transition[]) => void;
  setInitialContextVars: (vars: ContextVariable[]) => void;
  setContextVariables: (vars: ContextVariable[]) => void;
  setCanvasKey: (updater: (prev: number) => number) => void;
  setSelectedState: (state: WorkflowState | null) => void;
}

// --- Return type ---

export interface UseWorkflowPersistenceReturn {
  currentWorkflowId: string | null;
  currentWorkflowName: string;
  currentWorkflowDescription: string;
  saveDialogVisible: boolean;
  listPanelVisible: boolean;
  setSaveDialogVisible: (visible: boolean) => void;
  setListPanelVisible: (visible: boolean) => void;
  handleSaveClick: () => void;
  handleSaveWorkflow: (name: string, description: string) => Promise<void>;
  handleOpenWorkflow: (id: string) => Promise<void>;
  handleNewWorkflow: () => void;
  handleExport: () => void;
  handleImportFile: (event: React.ChangeEvent<HTMLInputElement>) => Promise<void>;
  fileInputRef: React.RefObject<HTMLInputElement>;
  handleImportClick: () => void;
}

// --- Hook implementation ---

export function useWorkflowPersistence(
  options: UseWorkflowPersistenceOptions
): UseWorkflowPersistenceReturn {
  const {
    canvasState,
    showToast,
    setInitialStates,
    setInitialTransitions,
    setInitialContextVars,
    setContextVariables,
    setCanvasKey,
    setSelectedState,
  } = options;

  // Workflow persistence state
  const [currentWorkflowId, setCurrentWorkflowId] = useState<string | null>(null);
  const [currentWorkflowName, setCurrentWorkflowName] = useState<string>('');
  const [currentWorkflowDescription, setCurrentWorkflowDescription] = useState<string>('');
  const [saveDialogVisible, setSaveDialogVisible] = useState(false);
  const [listPanelVisible, setListPanelVisible] = useState(false);

  // File input ref for import
  const fileInputRef = useRef<HTMLInputElement>(null);

  // Use a ref to access the latest canvasState without triggering re-renders
  const canvasStateRef = useRef<CanvasState | null>(canvasState);
  canvasStateRef.current = canvasState;

  // Use refs for workflow name/description to avoid stale closures in handleSaveClick
  const currentWorkflowIdRef = useRef<string | null>(currentWorkflowId);
  currentWorkflowIdRef.current = currentWorkflowId;
  const currentWorkflowNameRef = useRef<string>(currentWorkflowName);
  currentWorkflowNameRef.current = currentWorkflowName;
  const currentWorkflowDescriptionRef = useRef<string>(currentWorkflowDescription);
  currentWorkflowDescriptionRef.current = currentWorkflowDescription;

  // --- Save workflow ---
  const handleSaveWorkflow = useCallback(
    async (name: string, description: string) => {
      if (!canvasStateRef.current) return;
      setSaveDialogVisible(false);
      try {
        if (currentWorkflowIdRef.current) {
          await updateWorkflow(currentWorkflowIdRef.current, name, description, canvasStateRef.current);
          setCurrentWorkflowName(name);
          setCurrentWorkflowDescription(description);
          showToast('Workflow saved successfully', 'success');
        } else {
          const result = await saveWorkflow(name, description, canvasStateRef.current);
          setCurrentWorkflowId(result.id);
          setCurrentWorkflowName(name);
          setCurrentWorkflowDescription(description);
          showToast('Workflow created successfully', 'success');
        }
      } catch (err: unknown) {
        const message =
          err instanceof Error ? err.message : 'Failed to save workflow';
        showToast(message);
      }
    },
    [showToast]
  );

  const handleSaveClick = useCallback(() => {
    if (!canvasStateRef.current) return;
    if (currentWorkflowIdRef.current) {
      // Existing workflow: directly update
      handleSaveWorkflow(currentWorkflowNameRef.current, currentWorkflowDescriptionRef.current);
    } else {
      // New workflow: show dialog
      setSaveDialogVisible(true);
    }
  }, [handleSaveWorkflow]);

  // --- Open workflow ---
  const handleOpenWorkflow = useCallback(
    async (id: string) => {
      try {
        const response = await loadWorkflow(id);
        const { definition } = response;

        // Convert definition states/transitions to canvas format
        const states: WorkflowState[] = definition.states.map((s) => ({
          id: s.id,
          type: s.type,
          name: s.name,
          position: s.position,
          config: s.config,
          retryPolicy: s.retryPolicy,
          outputMapping: s.outputMapping,
        }));

        const transitions: Transition[] = definition.transitions.map((t) => ({
          id: t.id,
          source: t.source,
          target: t.target,
          condition: t.condition,
        }));

        // Update state to render loaded workflow
        setCurrentWorkflowId(response.id);
        setCurrentWorkflowName(response.name);
        setCurrentWorkflowDescription(response.description);
        setInitialStates(states);
        setInitialTransitions(transitions);
        const contextVarsWithIds = definition.contextVariables.map((cv) => ({
          ...cv,
          id: cv.id || crypto.randomUUID(),
        }));
        setInitialContextVars(contextVarsWithIds);
        setContextVariables(contextVarsWithIds);
        setCanvasKey((k) => k + 1); // Force canvas re-mount
        setListPanelVisible(false);
        setSelectedState(null);
        showToast('Workflow loaded successfully', 'success');
      } catch (err: unknown) {
        const message =
          err instanceof Error ? err.message : 'Failed to load workflow';
        showToast(message);
      }
    },
    [showToast, setInitialStates, setInitialTransitions, setInitialContextVars, setContextVariables, setCanvasKey, setSelectedState]
  );

  // --- New workflow ---
  const handleNewWorkflow = useCallback(() => {
    setCurrentWorkflowId(null);
    setCurrentWorkflowName('');
    setCurrentWorkflowDescription('');
    setInitialStates([]);
    setInitialTransitions([]);
    setInitialContextVars([]);
    setContextVariables([]);
    setCanvasKey((k) => k + 1);
    setListPanelVisible(false);
    setSelectedState(null);
  }, [setInitialStates, setInitialTransitions, setInitialContextVars, setContextVariables, setCanvasKey, setSelectedState]);

  // --- Export workflow ---
  const handleExport = useCallback(() => {
    if (!canvasStateRef.current) {
      showToast('No workflow to export');
      return;
    }
    const name = currentWorkflowNameRef.current || 'untitled';
    exportWorkflow(canvasStateRef.current, name, currentWorkflowDescriptionRef.current);
    showToast('Workflow exported successfully', 'success');
  }, [showToast]);

  // --- Import workflow ---
  const handleImportClick = useCallback(() => {
    fileInputRef.current?.click();
  }, []);

  const handleImportFile = useCallback(
    async (event: React.ChangeEvent<HTMLInputElement>) => {
      const file = event.target.files?.[0];
      if (!file) return;

      // Reset file input so the same file can be re-imported
      event.target.value = '';

      const result = await importWorkflow(file);
      if (!result.success) {
        showToast(result.error);
        return;
      }

      // Render imported workflow on canvas
      setCurrentWorkflowId(null);
      setCurrentWorkflowName(result.name);
      setCurrentWorkflowDescription(result.description);
      setInitialStates(result.states);
      setInitialTransitions(result.transitions);
      setInitialContextVars(result.contextVariables);
      setContextVariables(result.contextVariables);
      setCanvasKey((k) => k + 1);
      setSelectedState(null);
      showToast('Workflow imported successfully', 'success');
    },
    [showToast, setInitialStates, setInitialTransitions, setInitialContextVars, setContextVariables, setCanvasKey, setSelectedState]
  );

  return {
    currentWorkflowId,
    currentWorkflowName,
    currentWorkflowDescription,
    saveDialogVisible,
    listPanelVisible,
    setSaveDialogVisible,
    setListPanelVisible,
    handleSaveClick,
    handleSaveWorkflow,
    handleOpenWorkflow,
    handleNewWorkflow,
    handleExport,
    handleImportFile,
    fileInputRef,
    handleImportClick,
  };
}
