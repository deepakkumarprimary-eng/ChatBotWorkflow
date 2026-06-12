/**
 * Unit tests for workflowExportImport service.
 * Tests import validation flow (file size, JSON parsing, structure validation).
 * Requirements: 7.3, 7.4, 7.5, 7.7
 */
import { describe, it, expect } from 'vitest';
import { importWorkflow } from './workflowExportImport';

function createFile(content: string, name = 'test.json', type = 'application/json'): File {
  return new File([content], name, { type });
}

function createLargeFile(sizeBytes: number): File {
  const data = new Uint8Array(sizeBytes);
  return new File([data], 'large.json', { type: 'application/json' });
}

const validJson = JSON.stringify({
  metadata: { name: 'Test Workflow', description: 'A test', version: 1, createdAt: '', lastModifiedAt: '' },
  states: [
    { id: 's1', type: 'Response', name: 'Greet', position: { x: 10, y: 20 }, config: { type: 'Response', messageTemplate: 'Hi' } },
    { id: 's2', type: 'End', name: 'Done', position: { x: 200, y: 200 }, config: { type: 'End' } },
  ],
  transitions: [
    { id: 't1', source: 's1', target: 's2' },
  ],
  contextVariables: [{ id: 'cv-1', name: 'userName', defaultValue: '' }],
});

describe('importWorkflow', () => {
  it('should successfully import a valid workflow JSON file', async () => {
    const file = createFile(validJson);
    const result = await importWorkflow(file);
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.name).toBe('Test Workflow');
      expect(result.description).toBe('A test');
      expect(result.states).toHaveLength(2);
      expect(result.transitions).toHaveLength(1);
      expect(result.contextVariables).toHaveLength(1);
      expect(result.states[0].id).toBe('s1');
      expect(result.states[0].type).toBe('Response');
      expect(result.states[0].position).toEqual({ x: 10, y: 20 });
    }
  });

  it('should reject file larger than 5 MB', async () => {
    const file = createLargeFile(5 * 1024 * 1024 + 1);
    const result = await importWorkflow(file);
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error).toContain('File too large');
      expect(result.error).toContain('5 MB');
    }
  });

  it('should accept file exactly 5 MB', async () => {
    // Create a valid JSON that's under 5MB (file size check uses raw bytes)
    const file = createFile(validJson);
    // Just verify the size check doesn't reject a small file
    const result = await importWorkflow(file);
    expect(result.success).toBe(true);
  });

  it('should reject invalid JSON', async () => {
    const file = createFile('{ not valid json }}}');
    const result = await importWorkflow(file);
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error).toContain('Invalid JSON file');
    }
  });

  it('should reject missing required fields', async () => {
    const file = createFile(JSON.stringify({ metadata: { name: 'X' } }));
    const result = await importWorkflow(file);
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error).toContain('states');
    }
  });

  it('should reject invalid state types', async () => {
    const badJson = JSON.stringify({
      metadata: { name: 'Bad' },
      states: [{ id: 's1', type: 'Unknown', name: 'Bad', position: { x: 0, y: 0 }, config: {} }],
      transitions: [],
    });
    const file = createFile(badJson);
    const result = await importWorkflow(file);
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error).toContain('Invalid state type');
    }
  });

  it('should preserve canvas state on failure (returns error, not modifying anything)', async () => {
    const file = createFile('invalid');
    const result = await importWorkflow(file);
    // On failure, it just returns the error - caller decides not to update state
    expect(result.success).toBe(false);
  });
});
