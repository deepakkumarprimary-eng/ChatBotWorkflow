/**
 * ExecutionMonitor component displays real-time execution status,
 * highlights active state on canvas, shows execution history timeline,
 * elapsed time, and context variable values.
 *
 * Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.7
 */
import { useState, useEffect, useCallback, useRef } from 'react';
import axios from 'axios';
import type { ExecutionResponse, ExecutionStatus } from '../../types/api.types';
import styles from './ExecutionMonitor.module.css';

const API_BASE = 'http://localhost:8080/api';

const STATUS_CLASS: Record<ExecutionStatus, string> = {
  running: styles.statusRunning,
  completed: styles.statusCompleted,
  failed: styles.statusFailed,
  paused: styles.statusPaused,
};

export interface ExecutionMonitorProps {
  executionId: string | null;
  onClose: () => void;
  onHighlightState?: (stateId: string | null) => void;
}

function formatElapsedTime(ms: number): string {
  const totalSeconds = Math.floor(ms / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}m ${seconds}s`;
}

function formatTime(iso: string | null): string {
  if (!iso) return '—';
  try {
    return new Date(iso).toLocaleTimeString(undefined, {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    });
  } catch {
    return iso;
  }
}

function historyEntryClass(outcome: string): string {
  switch (outcome) {
    case 'succeeded':
      return `${styles.historyEntry} ${styles.historyEntrySucceeded}`;
    case 'failed':
      return `${styles.historyEntry} ${styles.historyEntryFailed}`;
    case 'timed_out':
      return `${styles.historyEntry} ${styles.historyEntryTimedOut}`;
    default:
      return styles.historyEntry;
  }
}

export default function ExecutionMonitor({
  executionId,
  onClose,
  onHighlightState,
}: ExecutionMonitorProps) {
  const [execution, setExecution] = useState<ExecutionResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [contextExpanded, setContextExpanded] = useState(false);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const fetchExecution = useCallback(async (id: string, signal?: AbortSignal) => {
    try {
      const response = await axios.get<ExecutionResponse>(`${API_BASE}/executions/${id}`, { signal });
      setExecution(response.data);
      setError(null);
      return response.data;
    } catch (err: unknown) {
      if (axios.isCancel(err)) return null; // Request was cancelled — no state update
      if (axios.isAxiosError(err) && err.response?.status === 404) {
        setError('Execution not found');
      } else {
        setError('Failed to load execution status');
      }
      return null;
    }
  }, []);

  useEffect(() => {
    if (!executionId) {
      setExecution(null);
      setError(null);
      onHighlightState?.(null);
      return;
    }

    const abortController = new AbortController();

    // Initial fetch
    fetchExecution(executionId, abortController.signal);

    // Poll every 2 seconds
    intervalRef.current = setInterval(async () => {
      const data = await fetchExecution(executionId, abortController.signal);
      if (data && (data.status === 'completed' || data.status === 'failed')) {
        // Stop polling when execution is terminal
        if (intervalRef.current) {
          clearInterval(intervalRef.current);
          intervalRef.current = null;
        }
      }
    }, 2000);

    return () => {
      abortController.abort();
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
        intervalRef.current = null;
      }
      onHighlightState?.(null);
    };
  }, [executionId, fetchExecution, onHighlightState]);

  // Notify canvas of current state to highlight
  useEffect(() => {
    if (execution?.currentStateId) {
      onHighlightState?.(execution.currentStateId);
    }
  }, [execution?.currentStateId, onHighlightState]);

  if (!executionId) return null;

  return (
    <div data-testid="execution-monitor" className={styles.panel}>
      {/* Header */}
      <div className={styles.header}>
        <h3 className={styles.title}>Execution Monitor</h3>
        <button
          data-testid="close-monitor-btn"
          onClick={onClose}
          className={styles.closeButton}
          aria-label="Close execution monitor"
        >
          ✕
        </button>
      </div>

      {/* Content */}
      <div className={styles.content}>
        {error && (
          <div data-testid="execution-error" className={styles.error}>
            {error}
          </div>
        )}

        {execution && !error && (
          <>
            {/* Status Badge */}
            <div className={styles.statusSection}>
              <div className={styles.statusRow}>
                <span
                  data-testid="execution-status-badge"
                  className={`${styles.statusBadge} ${STATUS_CLASS[execution.status]}`}
                >
                  {execution.status}
                </span>
              </div>
              <div className={styles.executionId}>
                ID: {execution.executionId.slice(0, 8)}…
              </div>
            </div>

            {/* Elapsed Time */}
            <div className={styles.section}>
              <div className={styles.sectionLabel}>Elapsed Time</div>
              <div data-testid="elapsed-time" className={styles.sectionValue}>
                {formatElapsedTime(execution.elapsedTimeMs)}
              </div>
            </div>

            {/* Current State */}
            {execution.currentStateId && (
              <div className={styles.section}>
                <div className={styles.sectionLabel}>Current State</div>
                <div data-testid="current-state-name" className={styles.sectionValue}>
                  {execution.history.length > 0
                    ? execution.history[execution.history.length - 1].stateName
                    : execution.currentStateId.slice(0, 8)}
                </div>
              </div>
            )}

            {/* Context Variables (collapsible) */}
            <div className={styles.section}>
              <button
                data-testid="toggle-context-vars"
                onClick={() => setContextExpanded(!contextExpanded)}
                className={styles.toggleButton}
              >
                <span>{contextExpanded ? '▼' : '▶'}</span>
                Context Variables
              </button>
              {contextExpanded && (
                <pre
                  data-testid="context-variables"
                  className={styles.contextVariables}
                >
                  {JSON.stringify(execution.contextVariables, null, 2)}
                </pre>
              )}
            </div>

            {/* History Timeline */}
            <div>
              <div className={styles.historyLabel}>Execution History</div>
              {execution.history.length === 0 && (
                <div className={styles.historyEmpty}>No history entries yet.</div>
              )}
              <div data-testid="execution-history">
                {execution.history.map((entry, idx) => (
                  <div
                    key={`${entry.stateId}-${entry.entryTime}`}
                    data-testid={`history-entry-${idx}`}
                    className={historyEntryClass(entry.outcome)}
                  >
                    <div className={styles.historyStateName}>{entry.stateName}</div>
                    <div className={styles.historyTime}>
                      {formatTime(entry.entryTime)} → {formatTime(entry.exitTime)}
                    </div>
                    <div className={styles.historyOutcome}>
                      Outcome: {entry.outcome}
                    </div>
                    {entry.error && (
                      <div className={styles.historyError}>
                        Error: {entry.error.message}
                      </div>
                    )}
                  </div>
                ))}
              </div>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
