/**
 * ValidationPanel component.
 * Provides a "Validate Workflow" button that runs client-side validation
 * and displays all errors simultaneously or a success message.
 *
 * Requirements: 4.6, 4.7, 4.8
 */
import { useState, useCallback } from 'react';
import type { WorkflowState, Transition } from '../../types/canvas.types';
import { validateWorkflow, type ValidationResult } from '../../utils/workflowValidator';
import styles from './ValidationPanel.module.css';

export interface ValidationPanelProps {
  states: Map<string, WorkflowState>;
  transitions: Map<string, Transition>;
}

export function ValidationPanel({ states, transitions }: ValidationPanelProps) {
  const [result, setResult] = useState<ValidationResult | null>(null);

  const handleValidate = useCallback(() => {
    const validationResult = validateWorkflow(states, transitions);
    setResult(validationResult);
  }, [states, transitions]);

  return (
    <div className={styles.panel}>
      <button
        onClick={handleValidate}
        className={styles.button}
        aria-label="Validate Workflow"
      >
        Validate Workflow
      </button>

      {result && (
        <div
          className={styles.results}
          role="status"
          aria-live="polite"
        >
          {result.valid ? (
            <div className={styles.success}>
              ✓ Workflow is ready for execution
            </div>
          ) : (
            <ul className={styles.errorList}>
              {result.errors.map((error) => (
                <li
                  key={`${error.stateId ?? 'workflow'}-${error.errorType}-${error.message}`}
                  className={styles.errorItem}
                >
                  <span className={styles.errorStateName}>
                    {error.stateName ? `[${error.stateName}] ` : ''}
                  </span>
                  {error.message}
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}
