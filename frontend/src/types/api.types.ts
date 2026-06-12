/**
 * API request and response type definitions for the Chatbot Workflow Builder.
 * These types correspond to the backend REST API contracts.
 *
 * Requirements: 1.1, 2.1, 7.1, 7.2
 */

import type {
  ContextVariable,
  Position,
  RetryPolicy,
  StateConfig,
  StateType,
  TransitionCondition,
} from './canvas.types';

// --- Workflow Definition (serialized format) ---

export interface WorkflowMetadata {
  name: string;
  description: string;
  version: number;
  createdAt: string; // ISO 8601
  lastModifiedAt: string; // ISO 8601
}

export interface StateDefinition {
  id: string;
  type: StateType;
  name: string;
  position: Position;
  config: StateConfig;
  retryPolicy?: RetryPolicy;
  outputMapping?: Record<string, string>;
}

export interface TransitionDefinition {
  id: string;
  source: string;
  target: string;
  condition?: TransitionCondition;
}

export interface WorkflowDefinition {
  metadata: WorkflowMetadata;
  states: StateDefinition[];
  transitions: TransitionDefinition[];
  contextVariables: ContextVariable[];
}

// --- Workflow CRUD ---

export interface WorkflowListItem {
  id: string;
  name: string;
  description: string;
  createdAt: string; // ISO 8601
  lastModifiedAt: string; // ISO 8601
  currentVersion: number;
}

export interface WorkflowListResponse {
  items: WorkflowListItem[];
  page: number;
  pageSize: number;
  totalCount: number;
}

export interface WorkflowResponse {
  id: string;
  name: string;
  description: string;
  currentVersion: number;
  createdAt: string; // ISO 8601
  lastModifiedAt: string; // ISO 8601
  definition: WorkflowDefinition;
}

export interface CreateWorkflowRequest {
  name: string;
  description: string;
  definition: WorkflowDefinition;
}

export interface UpdateWorkflowRequest {
  name: string;
  description: string;
  definition: WorkflowDefinition;
}

// --- Validation ---

export interface ValidationError {
  stateId: string;
  message: string;
}

export interface ValidationResult {
  valid: boolean;
  errors: ValidationError[];
}

// --- Execution ---

export type ExecutionStatus = 'running' | 'completed' | 'failed' | 'paused';

export type StateOutcome = 'succeeded' | 'failed' | 'skipped' | 'timed_out';

export interface ExecutionError {
  message: string;
  stackTrace?: string; // max 5000 chars
}

export interface ExecutionHistoryEntry {
  stateId: string;
  stateName: string;
  entryTime: string; // ISO 8601
  exitTime: string | null; // ISO 8601
  outcome: StateOutcome;
  error?: ExecutionError;
}

export interface ExecutionResponse {
  executionId: string;
  workflowId: string;
  workflowName: string;
  status: ExecutionStatus;
  currentStateId: string | null;
  startTime: string; // ISO 8601
  endTime: string | null; // ISO 8601
  elapsedTimeMs: number;
  contextVariables: Record<string, unknown>;
  history: ExecutionHistoryEntry[];
}

export interface ExecutionListItem {
  executionId: string;
  workflowId: string;
  workflowName: string;
  status: ExecutionStatus;
  startTime: string; // ISO 8601
  endTime: string | null; // ISO 8601
  elapsedTimeMs: number;
}

export interface ExecutionListResponse {
  items: ExecutionListItem[];
  page: number;
  pageSize: number;
  totalCount: number;
}

export interface StartExecutionResponse {
  executionId: string;
  status: ExecutionStatus;
}
