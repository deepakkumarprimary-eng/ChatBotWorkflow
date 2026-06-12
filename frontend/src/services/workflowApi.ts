/**
 * Workflow API service layer.
 * Handles serialization of canvas state to WorkflowDefinition JSON
 * and communicates with the backend REST API.
 *
 * Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6
 */
import axios from 'axios';
import type { CanvasState } from '../types/canvas.types';
import type {
  WorkflowDefinition,
  WorkflowResponse,
  WorkflowListResponse,
  StateDefinition,
  TransitionDefinition,
} from '../types/api.types';

const API_BASE = 'http://localhost:8080/api';

/**
 * Serialize the current canvas state into a WorkflowDefinition JSON structure.
 * Converts Map<string, WorkflowState> → states array and Map<string, Transition> → transitions array.
 */
export function serializeCanvasToDefinition(
  canvasState: CanvasState,
  name: string,
  description: string
): WorkflowDefinition {
  const states: StateDefinition[] = Array.from(canvasState.states.values()).map((s) => ({
    id: s.id,
    type: s.type,
    name: s.name,
    position: { x: s.position.x, y: s.position.y },
    config: s.config,
    retryPolicy: s.retryPolicy,
    outputMapping: s.outputMapping,
  }));

  const transitions: TransitionDefinition[] = Array.from(canvasState.transitions.values()).map((t) => ({
    id: t.id,
    source: t.source,
    target: t.target,
    condition: t.condition,
  }));

  return {
    metadata: {
      name,
      description,
      version: 1,
      createdAt: new Date().toISOString(),
      lastModifiedAt: new Date().toISOString(),
    },
    states,
    transitions,
    contextVariables: canvasState.contextVariables,
  };
}

/**
 * Save a new workflow to the backend.
 * POST /api/workflows
 */
export async function saveWorkflow(
  name: string,
  description: string,
  canvasState: CanvasState
): Promise<WorkflowResponse> {
  const definition = serializeCanvasToDefinition(canvasState, name, description);
  const response = await axios.post<WorkflowResponse>(`${API_BASE}/workflows`, {
    name,
    description,
    definition,
  });
  return response.data;
}

/**
 * Update an existing workflow.
 * PUT /api/workflows/{id}
 */
export async function updateWorkflow(
  id: string,
  name: string,
  description: string,
  canvasState: CanvasState
): Promise<WorkflowResponse> {
  const definition = serializeCanvasToDefinition(canvasState, name, description);
  const response = await axios.put<WorkflowResponse>(`${API_BASE}/workflows/${id}`, {
    name,
    description,
    definition,
  });
  return response.data;
}

/**
 * Load a workflow by ID from the backend.
 * GET /api/workflows/{id}
 */
export async function loadWorkflow(id: string): Promise<WorkflowResponse> {
  const response = await axios.get<WorkflowResponse>(`${API_BASE}/workflows/${id}`);
  return response.data;
}

/**
 * List workflows with pagination.
 * GET /api/workflows?page={page}&size={size}
 */
export async function listWorkflows(
  page: number = 0,
  size: number = 50
): Promise<WorkflowListResponse> {
  const response = await axios.get<WorkflowListResponse>(`${API_BASE}/workflows`, {
    params: { page, size },
  });
  return response.data;
}

/**
 * Delete a workflow by ID.
 * DELETE /api/workflows/{id}
 */
export async function deleteWorkflow(id: string): Promise<void> {
  await axios.delete(`${API_BASE}/workflows/${id}`);
}

/**
 * Execute a workflow by ID.
 * POST /api/workflows/{id}/execute
 * Returns 202 Accepted with the execution ID.
 *
 * Requirements: 5.1, 8.5
 */
export async function executeWorkflow(workflowId: string): Promise<{ executionId: string }> {
  const response = await axios.post<{ executionId: string }>(`${API_BASE}/workflows/${workflowId}/execute`);
  return response.data;
}
