/**
 * Panel displaying a list of saved workflows with pagination,
 * open/delete actions, and a "New Workflow" button.
 *
 * Requirements: 3.3, 3.4
 */
import { useReducer, useState, useEffect, useCallback, useRef } from 'react';
import type { WorkflowListItem } from '../../types/api.types';
import { listWorkflows, deleteWorkflow } from '../../services/workflowApi';
import styles from './WorkflowListPanel.module.css';

export interface WorkflowListPanelProps {
  visible: boolean;
  onOpen: (id: string) => void;
  onNew: () => void;
  onClose: () => void;
  onError: (message: string) => void;
}

/** Consolidated list state (Requirement 4.7) */
interface ListState {
  workflows: WorkflowListItem[];
  page: number;
  totalCount: number;
  loading: boolean;
}

type ListAction =
  | { type: 'FETCH_START' }
  | { type: 'FETCH_SUCCESS'; items: WorkflowListItem[]; totalCount: number; page: number }
  | { type: 'FETCH_DONE' };

function listReducer(state: ListState, action: ListAction): ListState {
  switch (action.type) {
    case 'FETCH_START':
      return { ...state, loading: true };
    case 'FETCH_SUCCESS':
      return { ...state, loading: false, workflows: action.items, totalCount: action.totalCount, page: action.page };
    case 'FETCH_DONE':
      return { ...state, loading: false };
  }
}

const INITIAL_LIST_STATE: ListState = {
  workflows: [],
  page: 0,
  totalCount: 0,
  loading: false,
};

export default function WorkflowListPanel({
  visible,
  onOpen,
  onNew,
  onClose,
  onError,
}: WorkflowListPanelProps) {
  const [state, dispatch] = useReducer(listReducer, INITIAL_LIST_STATE);
  const { workflows, page, totalCount, loading } = state;
  const [deleteConfirmId, setDeleteConfirmId] = useState<string | null>(null);
  const abortRef = useRef<AbortController | null>(null);

  const pageSize = 50;
  const totalPages = Math.max(1, Math.ceil(totalCount / pageSize));

  const fetchWorkflows = useCallback(
    async (pageNum: number) => {
      // Abort any in-flight request
      abortRef.current?.abort();
      const controller = new AbortController();
      abortRef.current = controller;

      dispatch({ type: 'FETCH_START' });
      try {
        const result = await listWorkflows(pageNum, pageSize);
        // Only update if not aborted
        if (!controller.signal.aborted) {
          dispatch({ type: 'FETCH_SUCCESS', items: result.items, totalCount: result.totalCount, page: pageNum });
        }
      } catch (err: unknown) {
        if (controller.signal.aborted) return; // Aborted — ignore
        dispatch({ type: 'FETCH_DONE' });
        const message =
          err instanceof Error ? err.message : 'Failed to load workflows';
        onError(message);
      }
    },
    [onError]
  );

  useEffect(() => {
    if (visible) {
      fetchWorkflows(0);
    }
    return () => {
      abortRef.current?.abort();
    };
  }, [visible, fetchWorkflows]);

  const handleDelete = useCallback(
    async (id: string) => {
      try {
        await deleteWorkflow(id);
        setDeleteConfirmId(null);
        // Refresh list after deletion
        await fetchWorkflows(page);
      } catch (err: unknown) {
        const message =
          err instanceof Error ? err.message : 'Failed to delete workflow';
        onError(message);
        setDeleteConfirmId(null);
      }
    },
    [page, fetchWorkflows, onError]
  );

  const formatDate = (isoDate: string): string => {
    try {
      return new Date(isoDate).toLocaleDateString(undefined, {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      });
    } catch {
      return isoDate;
    }
  };

  if (!visible) return null;

  return (
    <div
      data-testid="workflow-list-panel"
      role="dialog"
      aria-modal="true"
      aria-labelledby="workflow-list-title"
      className={styles.overlay}
    >
      <div className={styles.panel}>
        <div className={styles.header}>
          <h2 id="workflow-list-title" className={styles.title}>
            Saved Workflows
          </h2>
          <div className={styles.headerActions}>
            <button
              data-testid="new-workflow-btn"
              onClick={onNew}
              className={styles.newButton}
            >
              New Workflow
            </button>
            <button
              data-testid="close-list-btn"
              onClick={onClose}
              className={styles.closeButton}
            >
              Close
            </button>
          </div>
        </div>

        <div className={styles.listContainer}>
          {loading && <p className={styles.loadingText}>Loading...</p>}

          {!loading && workflows.length === 0 && (
            <p data-testid="empty-state" className={styles.emptyText}>
              No saved workflows. Create a new one to get started.
            </p>
          )}

          {!loading &&
            workflows.map((wf) => (
              <div
                key={wf.id}
                data-testid={`workflow-item-${wf.id}`}
                className={styles.workflowItem}
              >
                <div className={styles.workflowInfo}>
                  <div className={styles.workflowName}>
                    {wf.name}
                  </div>
                  {wf.description && (
                    <div className={styles.workflowDescription}>
                      {wf.description}
                    </div>
                  )}
                  <div className={styles.workflowDate}>
                    Modified: {formatDate(wf.lastModifiedAt)}
                  </div>
                </div>
                <div className={styles.itemActions}>
                  {deleteConfirmId === wf.id ? (
                    <>
                      <span className={styles.deleteConfirmText}>Delete?</span>
                      <button
                        data-testid={`confirm-delete-${wf.id}`}
                        onClick={() => handleDelete(wf.id)}
                        className={styles.confirmDeleteButton}
                      >
                        Yes
                      </button>
                      <button
                        data-testid={`cancel-delete-${wf.id}`}
                        onClick={() => setDeleteConfirmId(null)}
                        className={styles.cancelDeleteButton}
                      >
                        No
                      </button>
                    </>
                  ) : (
                    <>
                      <button
                        data-testid={`open-workflow-${wf.id}`}
                        onClick={() => onOpen(wf.id)}
                        className={styles.openButton}
                      >
                        Open
                      </button>
                      <button
                        data-testid={`delete-workflow-${wf.id}`}
                        onClick={() => setDeleteConfirmId(wf.id)}
                        className={styles.deleteButton}
                      >
                        Delete
                      </button>
                    </>
                  )}
                </div>
              </div>
            ))}
        </div>

        {/* Pagination */}
        {totalPages > 1 && (
          <div
            data-testid="pagination-controls"
            className={styles.pagination}
          >
            <button
              data-testid="prev-page-btn"
              onClick={() => fetchWorkflows(page - 1)}
              disabled={page === 0}
              className={styles.pageButton}
            >
              Previous
            </button>
            <span className={styles.pageInfo}>
              Page {page + 1} of {totalPages}
            </span>
            <button
              data-testid="next-page-btn"
              onClick={() => fetchWorkflows(page + 1)}
              disabled={page >= totalPages - 1}
              className={styles.pageButton}
            >
              Next
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
