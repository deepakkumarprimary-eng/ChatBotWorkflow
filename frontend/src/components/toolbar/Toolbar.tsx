/**
 * Toolbar component — extracted from App.tsx header.
 * Renders the application header with workflow action buttons.
 *
 * Accessibility (Requirements 8.1, 8.2, 8.3):
 * - All buttons use visible text as their accessible name (no redundant aria-label)
 * - If an icon-only button is added in the future, it MUST have an aria-label
 * - All buttons are native <button> elements, reachable via Tab key
 *
 * Requirements: 2.2, 2.7, 2.8, 3.1, 3.2
 */
import type { CanvasState } from '../../types/canvas.types';
import { ValidationPanel } from '../validation';
import styles from './Toolbar.module.css';

export interface ToolbarProps {
  workflowName: string;
  workflowId: string | null;
  onNew: () => void;
  onOpen: () => void;
  onSave: () => void;
  onExport: () => void;
  onImport: (event: React.ChangeEvent<HTMLInputElement>) => void;
  onImportClick: () => void;
  onExecute: () => void;
  onShowExecutions: () => void;
  canvasState: CanvasState | null;
  fileInputRef: React.RefObject<HTMLInputElement>;
}

export function Toolbar({
  workflowName,
  workflowId,
  onNew,
  onOpen,
  onSave,
  onExport,
  onImport,
  onImportClick,
  onExecute,
  onShowExecutions,
  canvasState,
  fileInputRef,
}: ToolbarProps) {
  return (
    <header className={styles.toolbar}>
      <h1 className={styles.title}>Chatbot Workflow Builder</h1>
      {workflowName && (
        <span className={styles.workflowName}>— {workflowName}</span>
      )}
      <div className={styles.actions}>
        <button
          data-testid="new-btn"
          onClick={onNew}
          className={styles.iconButton}
        >
          New
        </button>
        <button
          data-testid="open-btn"
          onClick={onOpen}
          className={styles.iconButton}
        >
          Open
        </button>
        <button
          data-testid="save-btn"
          onClick={onSave}
          className={styles.primaryButton}
        >
          Save
        </button>
        <button
          data-testid="export-btn"
          onClick={onExport}
          className={styles.iconButton}
        >
          Export
        </button>
        <button
          data-testid="import-btn"
          onClick={onImportClick}
          className={styles.iconButton}
        >
          Import
        </button>
        <input
          ref={fileInputRef}
          type="file"
          accept=".json"
          data-testid="import-file-input"
          onChange={onImport}
          className={styles.hiddenInput}
        />
        <button
          data-testid="executions-btn"
          onClick={onShowExecutions}
          className={styles.iconButton}
        >
          Executions
        </button>
        <button
          data-testid="execute-btn"
          data-role="execute"
          onClick={onExecute}
          disabled={!workflowId}
          title={!workflowId ? 'Save workflow first to execute' : 'Execute workflow'}
          className={workflowId ? styles.executeButton : styles.executeButtonDisabled}
        >
          Execute
        </button>
        <ValidationPanel
          states={canvasState?.states ?? new Map()}
          transitions={canvasState?.transitions ?? new Map()}
        />
      </div>
    </header>
  );
}
