/**
 * Client-side export and import functionality for workflow definitions.
 * Export creates a downloadable JSON file; Import reads and validates JSON files.
 *
 * Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7
 */

import type { CanvasState, WorkflowState, Transition, ContextVariable } from '../types/canvas.types';
import type { WorkflowDefinition } from '../types/api.types';
import { serializeCanvasToDefinition } from './workflowApi';
import { validateImportedDefinition } from '../utils/importValidator';

/** Maximum allowed import file size: 5 MB */
const MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024;

/**
 * Sanitizes a workflow name for use as a filename.
 * Removes special characters, replacing them with hyphens.
 */
function sanitizeFilename(name: string): string {
  return name
    .replace(/[^a-zA-Z0-9\s-_]/g, '')
    .replace(/\s+/g, '-')
    .replace(/-+/g, '-')
    .replace(/^-|-$/g, '')
    .toLowerCase() || 'workflow';
}

/**
 * Exports the current canvas state as a downloadable JSON file.
 * Filename format: {sanitized-workflow-name}-workflow.json
 */
export function exportWorkflow(
  canvasState: CanvasState,
  workflowName: string,
  description: string
): void {
  const definition = serializeCanvasToDefinition(canvasState, workflowName, description);
  const jsonString = JSON.stringify(definition, null, 2);
  const blob = new Blob([jsonString], { type: 'application/json' });

  const filename = `${sanitizeFilename(workflowName)}-workflow.json`;

  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}

export interface ImportResultSuccess {
  success: true;
  states: WorkflowState[];
  transitions: Transition[];
  contextVariables: ContextVariable[];
  name: string;
  description: string;
}

export interface ImportResultFailure {
  success: false;
  error: string;
}

export type ImportResult = ImportResultSuccess | ImportResultFailure;

/**
 * Imports a workflow from a JSON file.
 * Validates file size, JSON syntax, and structure before returning parsed data.
 * On failure, returns an error message without modifying any state.
 */
export async function importWorkflow(file: File): Promise<ImportResult> {
  // Validate file size (max 5 MB)
  if (file.size > MAX_FILE_SIZE_BYTES) {
    return {
      success: false,
      error: 'File too large. Maximum allowed file size is 5 MB.',
    };
  }

  // Read file contents
  let text: string;
  try {
    text = await file.text();
  } catch {
    return {
      success: false,
      error: 'Failed to read file.',
    };
  }

  // Parse JSON
  let json: unknown;
  try {
    json = JSON.parse(text);
  } catch {
    return {
      success: false,
      error: 'Invalid JSON file. The file does not contain valid JSON.',
    };
  }

  // Validate structure
  const validation = validateImportedDefinition(json);
  if (!validation.valid) {
    return {
      success: false,
      error: validation.error!,
    };
  }

  // Extract workflow data
  const definition = json as WorkflowDefinition;

  const states: WorkflowState[] = definition.states.map((s) => ({
    id: s.id,
    type: s.type,
    name: s.name || `${s.type} State`,
    position: s.position || { x: 0, y: 0 },
    config: s.config,
    retryPolicy: s.retryPolicy,
    outputMapping: s.outputMapping,
  }));

  const transitions: Transition[] = definition.transitions.map((t) => ({
    id: t.id || crypto.randomUUID(),
    source: t.source,
    target: t.target,
    condition: t.condition,
  }));

  const contextVariables: ContextVariable[] = (definition.contextVariables || []).map((cv) => ({
    id: cv.id || crypto.randomUUID(),
    name: cv.name,
    defaultValue: cv.defaultValue,
  }));

  return {
    success: true,
    states,
    transitions,
    contextVariables,
    name: definition.metadata.name,
    description: definition.metadata.description || '',
  };
}
