/**
 * Simple toast notification component for displaying connection validation errors.
 * Auto-dismisses after a configurable duration.
 *
 * Requirements: 1.4 - Display an error message when a connection is rejected
 */
import { useEffect, useState } from 'react';
import styles from './ConnectionToast.module.css';

export interface ToastMessage {
  id: string;
  message: string;
}

interface ConnectionToastProps {
  messages: ToastMessage[];
  onDismiss: (id: string) => void;
  /** Auto-dismiss duration in milliseconds (default: 3000) */
  duration?: number;
}

function ToastItem({
  message,
  onDismiss,
  duration = 3000,
}: {
  message: ToastMessage;
  onDismiss: (id: string) => void;
  duration?: number;
}) {
  const [visible, setVisible] = useState(true);

  useEffect(() => {
    const timer = setTimeout(() => {
      setVisible(false);
      setTimeout(() => onDismiss(message.id), 300);
    }, duration);
    return () => clearTimeout(timer);
  }, [message.id, onDismiss, duration]);

  return (
    <div
      data-testid="connection-toast"
      role="alert"
      className={`${styles.toast} ${visible ? styles.visible : styles.hidden}`}
    >
      {message.message}
    </div>
  );
}

export default function ConnectionToast({ messages, onDismiss, duration }: ConnectionToastProps) {
  if (messages.length === 0) return null;

  return (
    <div
      className={styles.container}
      data-testid="connection-toast-container"
    >
      {messages.map((msg) => (
        <ToastItem key={msg.id} message={msg} onDismiss={onDismiss} duration={duration} />
      ))}
    </div>
  );
}
