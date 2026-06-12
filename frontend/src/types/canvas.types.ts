/**
 * Core canvas and workflow type definitions for the Chatbot Workflow Builder.
 * These types define the state machine model used by the visual editor.
 *
 * Requirements: 1.1, 2.1, 7.1, 7.2
 */

// --- Position ---

export interface Position {
  x: number;
  y: number;
}

// --- State Types ---

export type StateType =
  | 'API_Call'
  | 'Condition'
  | 'Response'
  | 'Input'
  | 'Wait'
  | 'Parallel'
  | 'End';

// --- State Configuration Types ---

export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

export interface ApiCallConfig {
  type: 'API_Call';
  method: HttpMethod;
  url: string;
  headers: Record<string, string>;
  body: string;
  responseMapping: Record<string, string>;
  /** Timeout in seconds (1-120, default 30) */
  timeout: number;
}

export interface ConditionConfig {
  type: 'Condition';
  expression: string;
}

export interface ResponseConfig {
  type: 'Response';
  messageTemplate: string;
}

export interface InputConfig {
  type: 'Input';
  prompt: string;
  variableName: string;
  /** Timeout in seconds (default 300) */
  timeout: number;
}

export interface WaitConfig {
  type: 'Wait';
  /** Duration in seconds (1-86400) */
  duration: number;
}

export interface ParallelBranch {
  id: string;
  name: string;
  stateIds: string[];
}

export interface ParallelConfig {
  type: 'Parallel';
  /** Array of branch definitions (2-10 branches) */
  branches: ParallelBranch[];
}

export interface EndConfig {
  type: 'End';
}

/** Union type of all state-specific configurations */
export type StateConfig =
  | ApiCallConfig
  | ConditionConfig
  | ResponseConfig
  | InputConfig
  | WaitConfig
  | ParallelConfig
  | EndConfig;

// --- Retry Policy ---

export interface RetryPolicy {
  /** Maximum number of retries (0-10) */
  maxRetries: number;
  /** Base backoff interval in seconds (1-300) */
  backoffIntervalSeconds: number;
}

// --- Context Variables ---

export interface ContextVariable {
  id: string;
  name: string;
  defaultValue: unknown;
}

// --- Transition ---

export type TransitionCondition = 'true' | 'false' | 'error' | 'timeout' | 'fallback';

export interface Transition {
  id: string;
  source: string;
  target: string;
  condition?: TransitionCondition;
}

// --- Workflow State ---

export interface WorkflowState {
  id: string;
  type: StateType;
  name: string;
  position: Position;
  config: StateConfig;
  retryPolicy?: RetryPolicy;
  outputMapping?: Record<string, string>;
}

// --- Canvas Operations (Undo/Redo) ---

export type CanvasOperation =
  | { type: 'ADD_STATE'; state: WorkflowState }
  | { type: 'DELETE_STATE'; state: WorkflowState; transitions: Transition[] }
  | { type: 'MOVE_STATE'; stateId: string; from: Position; to: Position }
  | { type: 'ADD_TRANSITION'; transition: Transition }
  | { type: 'DELETE_TRANSITION'; transition: Transition }
  | { type: 'UPDATE_STATE_CONFIG'; stateId: string; oldConfig: StateConfig; newConfig: StateConfig };

// --- Canvas State ---

export interface CanvasState {
  states: Map<string, WorkflowState>;
  transitions: Map<string, Transition>;
  selectedStateId: string | null;
  /** Zoom level (0.25 to 4.0) */
  zoom: number;
  panOffset: Position;
  /** Undo stack with minimum 50 operation capacity */
  undoStack: CanvasOperation[];
  redoStack: CanvasOperation[];
  contextVariables: ContextVariable[];
}
