/**
 * Shared validation functions for workflow state configurations.
 * These validators enforce range constraints defined in the workflow specification.
 *
 * Requirements: 2.6, 2.7, 6.3, 9.1, 10.1
 */

const VARIABLE_NAME_REGEX = /^[a-zA-Z0-9_]{1,64}$/;

/**
 * Validates a context variable name.
 * Must be 1-64 alphanumeric or underscore characters.
 */
export function validateVariableName(name: string): string | null {
  if (!name) return 'Variable name is required';
  if (!VARIABLE_NAME_REGEX.test(name)) {
    return 'Must be 1-64 alphanumeric or underscore characters';
  }
  return null;
}

/**
 * Validates a Wait_State duration value.
 * Must be in the range [1, 86400] seconds.
 *
 * Requirement: 2.6
 */
export function validateWaitDuration(value: number): string | null {
  if (value < 1 || value > 86400) {
    return 'Duration must be between 1 and 86400 seconds';
  }
  return null;
}

/**
 * Validates an API_Call_State timeout value.
 * Must be in the range [1, 120] seconds.
 *
 * Requirement: 6.3
 */
export function validateApiTimeout(value: number): string | null {
  if (value < 1 || value > 120) {
    return 'Timeout must be between 1 and 120 seconds';
  }
  return null;
}

/**
 * Validates a Parallel_State branch count.
 * Must be in the range [2, 10].
 *
 * Requirement: 2.7
 */
export function validateParallelBranchCount(count: number): string | null {
  if (count < 2 || count > 10) {
    return 'Branch count must be between 2 and 10';
  }
  return null;
}

/**
 * Validates a retry policy maxRetries value.
 * Must be in the range [0, 10].
 *
 * Requirement: 9.1
 */
export function validateMaxRetries(value: number): string | null {
  if (value < 0 || value > 10) {
    return 'Max retries must be between 0 and 10';
  }
  return null;
}

/**
 * Validates a retry policy backoff interval value.
 * Must be in the range [1, 300] seconds.
 *
 * Requirement: 9.1
 */
export function validateBackoffInterval(value: number): string | null {
  if (value < 1 || value > 300) {
    return 'Backoff interval must be between 1 and 300 seconds';
  }
  return null;
}
