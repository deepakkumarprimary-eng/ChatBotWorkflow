/**
 * Simple toast notification component for displaying success/error messages.
 *
 * Requirements: 3.6
 */
import { useEffect } from 'react';

export interface ToastProps {
  message: string | null;
  type?: 'success' | 'error';
  onDismiss: () => void;
  /** Auto-dismiss after this many milliseconds (default: 4000) */
  duration?: number;
}

export default function Toast({ message, type = 'error', onDismiss, duration = 4000 }: ToastProps) {
  useEffect(() => {
    if (message) {
      const timer = setTimeout(onDismiss, duration);
      return () => clearTimeout(timer);
    }
  }, [message, onDismiss, duration]);

  if (!message) return null;

  const bgColor = type === 'error' ? '#d32f2f' : '#2e7d32';

  return (
    <div
      data-testid="toast-notification"
      role="alert"
      style={{
        position: 'fixed',
        bottom: '24px',
        left: '50%',
        transform: 'translateX(-50%)',
        background: bgColor,
        color: '#fff',
        padding: '12px 24px',
        borderRadius: '6px',
        fontSize: '14px',
        boxShadow: '0 4px 12px rgba(0,0,0,0.2)',
        zIndex: 2000,
        maxWidth: '80vw',
        textAlign: 'center',
      }}
      onClick={onDismiss}
    >
      {message}
    </div>
  );
}
