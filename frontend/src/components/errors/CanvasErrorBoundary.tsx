import { type ReactNode } from 'react';
import { ErrorBoundary } from './ErrorBoundary';
import styles from './ErrorBoundary.module.css';

interface CanvasErrorBoundaryProps {
  children: ReactNode;
  onReset: () => void;
}

export function CanvasErrorBoundary({ children, onReset }: CanvasErrorBoundaryProps) {
  return (
    <ErrorBoundary
      fallback={(_error, resetBoundary) => (
        <div className={styles.canvasFallback}>
          <h2>Canvas Error</h2>
          <p>Something went wrong in the workflow canvas.</p>
          <button
            className={styles.resetButton}
            onClick={() => {
              onReset();
              resetBoundary();
            }}
          >
            Reset Canvas
          </button>
        </div>
      )}
    >
      {children}
    </ErrorBoundary>
  );
}
