import { type ReactNode } from 'react';
import { ErrorBoundary } from './ErrorBoundary';
import styles from './ErrorBoundary.module.css';

export function AppErrorBoundary({ children }: { children: ReactNode }) {
  return (
    <ErrorBoundary
      fallback={
        <div className={styles.appFallback}>
          <h1>Something went wrong</h1>
          <p>An unexpected error occurred. Please reload the application.</p>
          <button className={styles.resetButton} onClick={() => window.location.reload()}>
            Reload Application
          </button>
        </div>
      }
    >
      {children}
    </ErrorBoundary>
  );
}
