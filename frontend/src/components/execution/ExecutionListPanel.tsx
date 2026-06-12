/**
 * ExecutionListPanel displays a paginated list of executions.
 * Users can click an execution to view its details in the ExecutionMonitor.
 *
 * Requirements: 8.5, 8.6
 */
import { useReducer, useEffect, useCallback, useRef } from 'react';
import axios from 'axios';
import type { ExecutionListItem, ExecutionListResponse, ExecutionStatus } from '../../types/api.types';

const API_BASE = 'http://localhost:8080/api';

const STATUS_COLORS: Record<ExecutionStatus, string> = {
  running: '#1976d2',
  completed: '#2e7d32',
  failed: '#d32f2f',
  paused: '#f9a825',
};

export interface ExecutionListPanelProps {
  visible: boolean;
  onSelectExecution: (id: string) => void;
  onClose: () => void;
}

function formatDate(iso: string | null): string {
  if (!iso) return '—';
  try {
    return new Date(iso).toLocaleString(undefined, {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  } catch {
    return iso;
  }
}

/** Consolidated list state (Requirement 4.7) */
interface ListState {
  executions: ExecutionListItem[];
  page: number;
  totalCount: number;
  loading: boolean;
  error: string | null;
}

type ListAction =
  | { type: 'FETCH_START' }
  | { type: 'FETCH_SUCCESS'; items: ExecutionListItem[]; totalCount: number; page: number }
  | { type: 'FETCH_ERROR'; error: string };

function listReducer(state: ListState, action: ListAction): ListState {
  switch (action.type) {
    case 'FETCH_START':
      return { ...state, loading: true, error: null };
    case 'FETCH_SUCCESS':
      return { ...state, loading: false, executions: action.items, totalCount: action.totalCount, page: action.page };
    case 'FETCH_ERROR':
      return { ...state, loading: false, error: action.error };
  }
}

const INITIAL_LIST_STATE: ListState = {
  executions: [],
  page: 0,
  totalCount: 0,
  loading: false,
  error: null,
};

export default function ExecutionListPanel({
  visible,
  onSelectExecution,
  onClose,
}: ExecutionListPanelProps) {
  const [state, dispatch] = useReducer(listReducer, INITIAL_LIST_STATE);
  const { executions, page, totalCount, loading, error } = state;
  const abortRef = useRef<AbortController | null>(null);

  const pageSize = 20;
  const totalPages = Math.max(1, Math.ceil(totalCount / pageSize));

  const fetchExecutions = useCallback(async (pageNum: number) => {
    // Abort any in-flight request
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;

    dispatch({ type: 'FETCH_START' });
    try {
      const response = await axios.get<ExecutionListResponse>(`${API_BASE}/executions`, {
        params: { page: pageNum, size: pageSize },
        signal: controller.signal,
      });
      dispatch({ type: 'FETCH_SUCCESS', items: response.data.items, totalCount: response.data.totalCount, page: pageNum });
    } catch (err: unknown) {
      if (axios.isCancel(err)) return; // Aborted — ignore
      if (axios.isAxiosError(err)) {
        dispatch({ type: 'FETCH_ERROR', error: 'Failed to load executions' });
      } else {
        dispatch({ type: 'FETCH_ERROR', error: err instanceof Error ? err.message : 'Failed to load executions' });
      }
    }
  }, []);

  useEffect(() => {
    if (visible) {
      fetchExecutions(0);
    }
    return () => {
      abortRef.current?.abort();
    };
  }, [visible, fetchExecutions]);

  if (!visible) return null;

  return (
    <div
      data-testid="execution-list-panel"
      role="dialog"
      aria-modal="true"
      aria-labelledby="execution-list-title"
      style={{
        position: 'fixed',
        inset: 0,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        backgroundColor: 'rgba(0, 0, 0, 0.5)',
        zIndex: 1000,
      }}
    >
      <div
        style={{
          background: '#fff',
          borderRadius: '8px',
          padding: '24px',
          width: '600px',
          maxWidth: '90vw',
          maxHeight: '80vh',
          display: 'flex',
          flexDirection: 'column',
          boxShadow: '0 4px 20px rgba(0,0,0,0.15)',
        }}
      >
        {/* Header */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
          <h2 id="execution-list-title" style={{ margin: 0, fontSize: '18px' }}>
            Executions
          </h2>
          <button
            data-testid="close-execution-list-btn"
            onClick={onClose}
            style={{
              padding: '6px 12px',
              border: '1px solid #ccc',
              borderRadius: '4px',
              background: '#fff',
              cursor: 'pointer',
              fontSize: '13px',
            }}
          >
            Close
          </button>
        </div>

        {/* Content */}
        <div style={{ flex: 1, overflowY: 'auto', minHeight: 0 }}>
          {loading && <p style={{ textAlign: 'center', color: '#666' }}>Loading...</p>}

          {error && (
            <div
              data-testid="execution-list-error"
              style={{
                padding: '12px',
                background: '#fdecea',
                borderRadius: '4px',
                color: '#d32f2f',
                fontSize: '13px',
                textAlign: 'center',
              }}
            >
              {error}
            </div>
          )}

          {!loading && !error && executions.length === 0 && (
            <p data-testid="empty-executions" style={{ textAlign: 'center', color: '#666', padding: '32px 0' }}>
              No executions found.
            </p>
          )}

          {!loading &&
            !error &&
            executions.map((exec) => (
              <div
                key={exec.executionId}
                data-testid={`execution-item-${exec.executionId}`}
                onClick={() => onSelectExecution(exec.executionId)}
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  padding: '12px',
                  borderBottom: '1px solid #eee',
                  cursor: 'pointer',
                  transition: 'background 0.15s',
                }}
                onMouseEnter={(e) => {
                  (e.currentTarget as HTMLDivElement).style.background = '#f5f5f5';
                }}
                onMouseLeave={(e) => {
                  (e.currentTarget as HTMLDivElement).style.background = 'transparent';
                }}
              >
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <span
                      style={{
                        fontSize: '13px',
                        fontFamily: 'monospace',
                        color: '#333',
                      }}
                    >
                      {exec.executionId.slice(0, 8)}…
                    </span>
                    <span
                      data-testid={`execution-status-${exec.executionId}`}
                      style={{
                        display: 'inline-block',
                        padding: '2px 8px',
                        borderRadius: '10px',
                        background: STATUS_COLORS[exec.status],
                        color: '#fff',
                        fontSize: '11px',
                        fontWeight: 500,
                        textTransform: 'capitalize',
                      }}
                    >
                      {exec.status}
                    </span>
                  </div>
                  <div style={{ fontSize: '12px', color: '#666', marginTop: '4px' }}>
                    {exec.workflowName}
                  </div>
                  <div style={{ fontSize: '11px', color: '#999', marginTop: '2px' }}>
                    Start: {formatDate(exec.startTime)}
                    {exec.endTime && ` • End: ${formatDate(exec.endTime)}`}
                  </div>
                </div>
              </div>
            ))}
        </div>

        {/* Pagination */}
        {totalPages > 1 && (
          <div
            data-testid="execution-pagination"
            style={{
              display: 'flex',
              justifyContent: 'center',
              alignItems: 'center',
              gap: '12px',
              marginTop: '16px',
              paddingTop: '12px',
              borderTop: '1px solid #eee',
            }}
          >
            <button
              data-testid="exec-prev-page-btn"
              onClick={() => fetchExecutions(page - 1)}
              disabled={page === 0}
              style={{
                padding: '4px 10px',
                border: '1px solid #ccc',
                borderRadius: '4px',
                background: page === 0 ? '#f5f5f5' : '#fff',
                cursor: page === 0 ? 'not-allowed' : 'pointer',
                fontSize: '12px',
              }}
            >
              Previous
            </button>
            <span style={{ fontSize: '12px', color: '#666' }}>
              Page {page + 1} of {totalPages}
            </span>
            <button
              data-testid="exec-next-page-btn"
              onClick={() => fetchExecutions(page + 1)}
              disabled={page >= totalPages - 1}
              style={{
                padding: '4px 10px',
                border: '1px solid #ccc',
                borderRadius: '4px',
                background: page >= totalPages - 1 ? '#f5f5f5' : '#fff',
                cursor: page >= totalPages - 1 ? 'not-allowed' : 'pointer',
                fontSize: '12px',
              }}
            >
              Next
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
