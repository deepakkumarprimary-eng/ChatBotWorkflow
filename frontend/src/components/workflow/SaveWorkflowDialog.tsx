/**
 * Modal dialog for saving a workflow.
 * Prompts user for workflow name (1-100 chars) and description.
 *
 * Requirements: 3.1, 3.2
 */
import { useState, useCallback, useEffect, useRef } from 'react';
import { useFocusTrap } from '../../hooks';
import styles from './SaveWorkflowDialog.module.css';

export interface SaveWorkflowDialogProps {
  visible: boolean;
  initialName?: string;
  initialDescription?: string;
  onSave: (name: string, description: string) => void;
  onCancel: () => void;
}

export default function SaveWorkflowDialog({
  visible,
  initialName = '',
  initialDescription = '',
  onSave,
  onCancel,
}: SaveWorkflowDialogProps) {
  const dialogRef = useRef<HTMLDivElement>(null);
  const [name, setName] = useState(initialName);
  const [description, setDescription] = useState(initialDescription);
  const [nameError, setNameError] = useState<string | null>(null);

  useFocusTrap(dialogRef, visible);

  useEffect(() => {
    if (visible) {
      setName(initialName);
      setDescription(initialDescription);
      setNameError(null);
    }
  }, [visible, initialName, initialDescription]);

  const validateName = useCallback((value: string): boolean => {
    if (!value.trim()) {
      setNameError('Workflow name is required');
      return false;
    }
    if (value.length > 100) {
      setNameError('Name must be 100 characters or less');
      return false;
    }
    setNameError(null);
    return true;
  }, []);

  const handleSave = useCallback(() => {
    if (validateName(name)) {
      onSave(name.trim(), description.trim());
    }
  }, [name, description, validateName, onSave]);

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent<HTMLDivElement>) => {
      if (e.key === 'Enter') {
        e.preventDefault();
        handleSave();
      } else if (e.key === 'Escape') {
        onCancel();
      }
    },
    [handleSave, onCancel]
  );

  if (!visible) return null;

  return (
    <div
      ref={dialogRef}
      data-testid="save-workflow-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="save-workflow-title"
      className={styles.overlay}
      onKeyDown={handleKeyDown}
    >
      <div className={styles.dialog}>
        <h2 id="save-workflow-title" className={styles.title}>
          Save Workflow
        </h2>

        <div className={styles.fieldGroup}>
          <label htmlFor="workflow-name" className={styles.label}>
            Name <span className={styles.required}>*</span>
          </label>
          <input
            id="workflow-name"
            data-testid="workflow-name-input"
            type="text"
            value={name}
            onChange={(e) => {
              setName(e.target.value);
              if (nameError) validateName(e.target.value);
            }}
            maxLength={100}
            placeholder="Enter workflow name"
            className={`${styles.input} ${nameError ? styles.inputError : ''}`}
            autoFocus
          />
          {nameError && (
            <span data-testid="name-error" className={styles.errorText}>
              {nameError}
            </span>
          )}
        </div>

        <div className={styles.fieldGroupLast}>
          <label htmlFor="workflow-description" className={styles.label}>
            Description
          </label>
          <textarea
            id="workflow-description"
            data-testid="workflow-description-input"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="Enter workflow description (optional)"
            rows={3}
            className={styles.textarea}
          />
        </div>

        <div className={styles.buttonRow}>
          <button
            data-testid="save-cancel-btn"
            onClick={onCancel}
            className={styles.cancelButton}
          >
            Cancel
          </button>
          <button
            data-testid="save-confirm-btn"
            onClick={handleSave}
            className={styles.saveButton}
          >
            Save
          </button>
        </div>
      </div>
    </div>
  );
}
