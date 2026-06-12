/**
 * Unit tests for importValidator - validateImportedDefinition utility.
 * Requirements: 7.4, 7.5, 7.6, 7.7
 */
import { describe, it, expect } from 'vitest';
import { validateImportedDefinition } from './importValidator';

describe('validateImportedDefinition', () => {
  const validDefinition = {
    metadata: { name: 'Test Workflow', description: '', version: 1, createdAt: '', lastModifiedAt: '' },
    states: [
      { id: 'state-1', type: 'Response', name: 'Start', position: { x: 0, y: 0 }, config: { type: 'Response', messageTemplate: 'Hello' } },
      { id: 'state-2', type: 'End', name: 'End', position: { x: 100, y: 100 }, config: { type: 'End' } },
    ],
    transitions: [
      { id: 't-1', source: 'state-1', target: 'state-2' },
    ],
    contextVariables: [],
  };

  it('should accept a valid workflow definition', () => {
    const result = validateImportedDefinition(validDefinition);
    expect(result.valid).toBe(true);
    expect(result.error).toBeUndefined();
  });

  it('should reject null input', () => {
    const result = validateImportedDefinition(null);
    expect(result.valid).toBe(false);
    expect(result.error).toContain('Invalid JSON structure');
  });

  it('should reject non-object input', () => {
    const result = validateImportedDefinition('not an object');
    expect(result.valid).toBe(false);
    expect(result.error).toContain('Invalid JSON structure');
  });

  it('should reject missing metadata', () => {
    const { metadata: _, ...noMetadata } = validDefinition;
    const result = validateImportedDefinition(noMetadata);
    expect(result.valid).toBe(false);
    expect(result.error).toContain('metadata');
  });

  it('should reject missing metadata.name', () => {
    const def = { ...validDefinition, metadata: { description: 'no name' } };
    const result = validateImportedDefinition(def);
    expect(result.valid).toBe(false);
    expect(result.error).toContain('metadata.name');
  });

  it('should reject missing states', () => {
    const { states: _, ...noStates } = validDefinition;
    const result = validateImportedDefinition(noStates);
    expect(result.valid).toBe(false);
    expect(result.error).toContain('states');
  });

  it('should reject non-array states', () => {
    const def = { ...validDefinition, states: 'not an array' };
    const result = validateImportedDefinition(def);
    expect(result.valid).toBe(false);
    expect(result.error).toContain('states must be an array');
  });

  it('should reject missing transitions', () => {
    const { transitions: _, ...noTransitions } = validDefinition;
    const result = validateImportedDefinition(noTransitions);
    expect(result.valid).toBe(false);
    expect(result.error).toContain('transitions');
  });

  it('should reject non-array transitions', () => {
    const def = { ...validDefinition, transitions: {} };
    const result = validateImportedDefinition(def);
    expect(result.valid).toBe(false);
    expect(result.error).toContain('transitions must be an array');
  });

  it('should reject a state with invalid type', () => {
    const def = {
      ...validDefinition,
      states: [
        { id: 's1', type: 'InvalidType', name: 'Bad', position: { x: 0, y: 0 }, config: {} },
      ],
    };
    const result = validateImportedDefinition(def);
    expect(result.valid).toBe(false);
    expect(result.error).toContain('Invalid state type "InvalidType"');
  });

  it('should reject a state missing id', () => {
    const def = {
      ...validDefinition,
      states: [
        { type: 'End', name: 'NoId', position: { x: 0, y: 0 }, config: {} },
      ],
    };
    const result = validateImportedDefinition(def);
    expect(result.valid).toBe(false);
    expect(result.error).toContain('missing or invalid id');
  });

  it('should reject a transition missing source', () => {
    const def = {
      ...validDefinition,
      transitions: [
        { id: 't1', target: 'state-2' },
      ],
    };
    const result = validateImportedDefinition(def);
    expect(result.valid).toBe(false);
    expect(result.error).toContain('missing or invalid source');
  });

  it('should reject a transition missing target', () => {
    const def = {
      ...validDefinition,
      transitions: [
        { id: 't1', source: 'state-1' },
      ],
    };
    const result = validateImportedDefinition(def);
    expect(result.valid).toBe(false);
    expect(result.error).toContain('missing or invalid target');
  });

  it('should accept all 7 valid state types', () => {
    const types = ['API_Call', 'Condition', 'Response', 'Input', 'Wait', 'Parallel', 'End'];
    for (const type of types) {
      const def = {
        ...validDefinition,
        states: [{ id: `s-${type}`, type, name: type, position: { x: 0, y: 0 }, config: {} }],
      };
      const result = validateImportedDefinition(def);
      expect(result.valid).toBe(true);
    }
  });
});
