import { useState, useCallback, useRef } from 'react';
import {
  WorkflowCanvas,
  ComponentPalette,
  PropertyPanel,
  SaveWorkflowDialog,
  WorkflowListPanel,
  Toast,
  ExecutionMonitor,
  ExecutionListPanel,
} from './components';
import { Toolbar } from './components/toolbar';
import { CanvasErrorBoundary } from './components/errors/CanvasErrorBoundary';
import { useWorkflowPersistence, useExecutionState } from './hooks';
import { WorkflowState, StateConfig, RetryPolicy, CanvasState, ContextVariable, Transition } from './types/canvas.types';
import styles from './App.module.css';

/** Grouped canvas initialization state — always updated together when loading/importing workflows */
interface CanvasInitState {
  key: number;
  states: WorkflowState[];
  transitions: Transition[];
  contextVars: ContextVariable[];
}

const EMPTY_CANVAS_INIT: CanvasInitState = {
  key: 0,
  states: [],
  transitions: [],
  contextVars: [],
};

/** Grouped toast state — message and type are always set together */
interface ToastState {
  message: string | null;
  type: 'success' | 'error';
}

const EMPTY_TOAST: ToastState = { message: null, type: 'error' };

/** Node interaction state — selected node and pending delete are always related */
interface NodeInteraction {
  selected: WorkflowState | null;
  deleteRequest: string | null;
}

const EMPTY_INTERACTION: NodeInteraction = { selected: null, deleteRequest: null };

function App() {
  // Canvas runtime state
  const [canvasState, setCanvasState] = useState<CanvasState | null>(null);
  const [contextVariables, setContextVariables] = useState<ContextVariable[]>([]);

  // Node interaction state (selected + delete request)
  const [nodeInteraction, setNodeInteraction] = useState<NodeInteraction>(EMPTY_INTERACTION);

  // Grouped canvas initialization state
  const [canvasInit, setCanvasInit] = useState<CanvasInitState>(EMPTY_CANVAS_INIT);

  // Grouped toast state
  const [toast, setToast] = useState<ToastState>(EMPTY_TOAST);

  const showToast = useCallback((message: string, type: 'success' | 'error' = 'error') => {
    setToast({ message, type });
  }, []);

  // Derived setter for selectedState (used by persistence hook)
  const setSelectedState = useCallback((state: WorkflowState | null) => {
    setNodeInteraction((prev) => ({ ...prev, selected: state }));
  }, []);

  // Stable setter callbacks for the persistence hook
  const setInitialStates = useCallback((states: WorkflowState[]) => {
    setCanvasInit((prev) => ({ ...prev, states }));
  }, []);

  const setInitialTransitions = useCallback((transitions: Transition[]) => {
    setCanvasInit((prev) => ({ ...prev, transitions }));
  }, []);

  const setInitialContextVars = useCallback((contextVars: ContextVariable[]) => {
    setCanvasInit((prev) => ({ ...prev, contextVars }));
  }, []);

  const setCanvasKey = useCallback((updater: (prev: number) => number) => {
    setCanvasInit((prev) => ({ ...prev, key: updater(prev.key) }));
  }, []);

  // --- Workflow persistence hook ---
  const persistence = useWorkflowPersistence({
    canvasState,
    showToast,
    setInitialStates,
    setInitialTransitions,
    setInitialContextVars,
    setContextVariables,
    setCanvasKey,
    setSelectedState,
  });

  // --- Execution state hook ---
  const execution = useExecutionState({
    currentWorkflowId: persistence.currentWorkflowId,
    showToast,
  });

  const canvasStateRef = useRef<CanvasState | null>(canvasState);
  canvasStateRef.current = canvasState;

  const handleNodeSelect = useCallback(
    (stateId: string | null) => {
      if (!stateId || !canvasStateRef.current) {
        setNodeInteraction((prev) => ({ ...prev, selected: null }));
        return;
      }
      const state = canvasStateRef.current.states.get(stateId) ?? null;
      setNodeInteraction((prev) => ({ ...prev, selected: state }));
    },
    []
  );

  const handleCanvasStateChange = useCallback((state: CanvasState) => {
    setCanvasState(state);
    // Keep selected state in sync with latest canvas state
    setNodeInteraction((prev) => {
      if (!prev.selected) return prev;
      const updated = state.states.get(prev.selected.id) ?? null;
      return { ...prev, selected: updated };
    });
  }, []);

  const handleConfigChange = useCallback(
    (stateId: string, config: StateConfig) => {
      setNodeInteraction((prev) => {
        if (!prev.selected || prev.selected.id !== stateId) return prev;
        return { ...prev, selected: { ...prev.selected, config } };
      });
    },
    []
  );

  const handleDeleteState = useCallback((stateId: string) => {
    setNodeInteraction((prev) => ({ ...prev, deleteRequest: stateId }));
  }, []);

  const handleDeleteRequestHandled = useCallback(() => {
    setNodeInteraction((prev) => ({ ...prev, deleteRequest: null }));
  }, []);

  const handleRetryPolicyChange = useCallback(
    (stateId: string, policy: RetryPolicy | undefined) => {
      setNodeInteraction((prev) => {
        if (!prev.selected || prev.selected.id !== stateId) return prev;
        return { ...prev, selected: { ...prev.selected, retryPolicy: policy } };
      });
    },
    []
  );

  // Stable callbacks for child components (Requirement 4.2)
  const handleOpenToolbar = useCallback(() => {
    persistence.setListPanelVisible(true);
  }, [persistence]);

  const handleShowExecutions = useCallback(() => {
    execution.setExecutionListVisible(true);
  }, [execution]);

  const handleSaveDialogCancel = useCallback(() => {
    persistence.setSaveDialogVisible(false);
  }, [persistence]);

  const handleListPanelClose = useCallback(() => {
    persistence.setListPanelVisible(false);
  }, [persistence]);

  const handleListPanelError = useCallback(
    (msg: string) => showToast(msg),
    [showToast]
  );

  const handleToastDismiss = useCallback(() => {
    setToast(EMPTY_TOAST);
  }, []);

  const handleSelectExecution = useCallback(
    (id: string) => {
      execution.setActiveExecutionId(id);
      execution.setExecutionListVisible(false);
    },
    [execution]
  );

  const handleExecutionListClose = useCallback(() => {
    execution.setExecutionListVisible(false);
  }, [execution]);

  const handleExecutionMonitorClose = useCallback(() => {
    execution.setActiveExecutionId(null);
  }, [execution]);

  const handleHighlightState = useCallback(() => {
    // Canvas highlight integration - could be wired to canvas in future
  }, []);

  const handleCanvasReset = useCallback(() => {
    setCanvasInit((prev) => ({ ...prev, key: prev.key + 1 }));
  }, []);

  return (
    <div className={styles.app}>
      <Toolbar
        workflowName={persistence.currentWorkflowName}
        workflowId={persistence.currentWorkflowId}
        onNew={persistence.handleNewWorkflow}
        onOpen={handleOpenToolbar}
        onSave={persistence.handleSaveClick}
        onExport={persistence.handleExport}
        onImport={persistence.handleImportFile}
        onImportClick={persistence.handleImportClick}
        onExecute={execution.handleExecute}
        onShowExecutions={handleShowExecutions}
        canvasState={canvasState}
        fileInputRef={persistence.fileInputRef}
      />
      <main className={styles.main}>
        <ComponentPalette />
        <div role="application" aria-label="Workflow editor canvas" className={styles.canvasArea}>
          <CanvasErrorBoundary onReset={handleCanvasReset}>
            <WorkflowCanvas
              key={canvasInit.key}
              initialStates={canvasInit.states}
              initialTransitions={canvasInit.transitions}
              initialContextVariables={canvasInit.contextVars}
              onNodeSelect={handleNodeSelect}
              onCanvasStateChange={handleCanvasStateChange}
              deleteStateRequest={nodeInteraction.deleteRequest}
              onDeleteRequestHandled={handleDeleteRequestHandled}
            />
          </CanvasErrorBoundary>
        </div>
        <PropertyPanel
          selectedState={nodeInteraction.selected}
          onConfigChange={handleConfigChange}
          onRetryPolicyChange={handleRetryPolicyChange}
          contextVariables={contextVariables}
          onContextVariablesChange={setContextVariables}
          onDeleteState={handleDeleteState}
        />
      </main>

      {/* Save Workflow Dialog */}
      <SaveWorkflowDialog
        visible={persistence.saveDialogVisible}
        initialName={persistence.currentWorkflowName}
        initialDescription={persistence.currentWorkflowDescription}
        onSave={persistence.handleSaveWorkflow}
        onCancel={handleSaveDialogCancel}
      />

      {/* Workflow List Panel */}
      <WorkflowListPanel
        visible={persistence.listPanelVisible}
        onOpen={persistence.handleOpenWorkflow}
        onNew={persistence.handleNewWorkflow}
        onClose={handleListPanelClose}
        onError={handleListPanelError}
      />

      {/* Toast Notification */}
      <Toast
        message={toast.message}
        type={toast.type}
        onDismiss={handleToastDismiss}
      />

      {/* Execution List Panel */}
      <ExecutionListPanel
        visible={execution.executionListVisible}
        onSelectExecution={handleSelectExecution}
        onClose={handleExecutionListClose}
      />

      {/* Execution Monitor */}
      <ExecutionMonitor
        executionId={execution.activeExecutionId}
        onClose={handleExecutionMonitorClose}
        onHighlightState={handleHighlightState}
      />
    </div>
  );
}

export default App;
