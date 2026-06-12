/**
 * Property-based tests for range validations using fast-check.
 * Tests that numeric range validation functions correctly accept/reject values.
 *
 * Feature: chatbot-workflow-builder
 * Validates: Requirements 2.6, 2.7
 */
import * as fc from 'fast-check';
import { validateWaitDuration, validateParallelBranchCount } from '../utils/validators';

// =============================================================================
// Property 7: Wait duration range validation
// =============================================================================

describe('Property 7: Wait duration range validation', () => {
  /**
   * **Validates: Requirements 2.6**
   *
   * For any integer value, it should be accepted as a Wait_State duration
   * if and only if it is in the range [1, 86400].
   */

  it('values in [1, 86400] are accepted (return null)', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 1, max: 86400 }),
        (value) => {
          const result = validateWaitDuration(value);
          expect(result).toBeNull();
        }
      ),
      { numRuns: 100 }
    );
  });

  it('values below 1 are rejected (return error string)', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: -1_000_000, max: 0 }),
        (value) => {
          const result = validateWaitDuration(value);
          expect(result).not.toBeNull();
          expect(typeof result).toBe('string');
        }
      ),
      { numRuns: 100 }
    );
  });

  it('values above 86400 are rejected (return error string)', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 86401, max: 1_000_000 }),
        (value) => {
          const result = validateWaitDuration(value);
          expect(result).not.toBeNull();
          expect(typeof result).toBe('string');
        }
      ),
      { numRuns: 100 }
    );
  });
});

// =============================================================================
// Property 8: Parallel branch count validation
// =============================================================================

describe('Property 8: Parallel branch count validation', () => {
  /**
   * **Validates: Requirements 2.7**
   *
   * For any integer value, it should be accepted as a Parallel_State branch count
   * if and only if it is in the range [2, 10].
   */

  it('values in [2, 10] are accepted (return null)', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 2, max: 10 }),
        (value) => {
          const result = validateParallelBranchCount(value);
          expect(result).toBeNull();
        }
      ),
      { numRuns: 100 }
    );
  });

  it('values below 2 are rejected (return error string)', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: -1_000_000, max: 1 }),
        (value) => {
          const result = validateParallelBranchCount(value);
          expect(result).not.toBeNull();
          expect(typeof result).toBe('string');
        }
      ),
      { numRuns: 100 }
    );
  });

  it('values above 10 are rejected (return error string)', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 11, max: 1_000_000 }),
        (value) => {
          const result = validateParallelBranchCount(value);
          expect(result).not.toBeNull();
          expect(typeof result).toBe('string');
        }
      ),
      { numRuns: 100 }
    );
  });
});
