/**
 * Confirmation dialog for state deletion.
 * Displays the state name and number of associated transitions that will be removed.
 *
 * Requirements: 1.7 - State deletion with confirmation
 */
import { useEffect, useRef } from 'react';
import { useFocusTrap } from '../../hooks';
import styles from './ConfirmDeleteDialog.module.css';

export interface ConfirmDeleteDialogProps {
  visible: boolean;
  stateName: string;
  transitionCount: number;
  onConfirm: () => void;
  onCancel: () => void;
}

export default function ConfirmDeleteDialog({
  visible,
  stateName,
  transitionCount,
  onConfirm,
  onCancel,
}: ConfirmDeleteDialogProps) {
  const cancelRef = useRef<HTMLButtonElement>(null);
  const dialogRef = useRef<HTMLDivElement>(null);

  useFocusTrap(dialogRef, visible);

  // Handle Escape key to close
  useEffect(() => {
    if (!visible) return;

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        onCancel();
      }
    };

    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [visible, onCancel]);

  if (!visible) return null;

  const transitionText =
    transitionCount === 1
      ? '1 associated transition'
      : `${transitionCount} associated transition(s)`;

  return (
    <div
      className={styles.overlay}
      onClick={onCancel}
      data-testid="confirm-delete-overlay"
    >
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="confirm-delete-title"
        aria-describedby="confirm-delete-message"
        className={styles.dialog}
        onClick={(e) => e.stopPropagation()}
        data-testid="confirm-delete-dialog"
      >
        <h2 id="confirm-delete-title" className={styles.title}>
          Delete State
        </h2>
        <p id="confirm-delete-message" className={styles.message}>
          Are you sure you want to delete &quot;{stateName}&quot; and its {transitionText}?
        </p>
        <div className={styles.buttonRow}>
          <button
            ref={cancelRef}
            className={styles.cancelButton}
            onClick={onCancel}
            data-testid="confirm-delete-cancel"
          >
            Cancel
          </button>
          <button
            className={styles.deleteButton}
            onClick={onConfirm}
            data-testid="confirm-delete-confirm"
          >
            Delete
          </button>
        </div>
      </div>
    </div>
  );
}
