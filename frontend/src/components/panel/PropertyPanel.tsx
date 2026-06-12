/**
 * Dynamic Property Panel component.
 * Displays and edits configuration for the selected workflow state.
 * Renders different fields based on the state type with inline validation.
 *
 * Requirements: 1.5, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 9.1, 9.4
 */
import { useState, useCallback } from 'react';
import {
  WorkflowState,
  StateConfig,
  RetryPolicy,
  ContextVariable,
  ApiCallConfig,
  ConditionConfig,
  ResponseConfig,
  InputConfig,
  WaitConfig,
  ParallelConfig,
  ParallelBranch,
  HttpMethod,
} from '../../types/canvas.types';
import ContextVariablesPanel from './ContextVariablesPanel';
import {
  validateVariableName,
  validateWaitDuration,
  validateApiTimeout,
  validateParallelBranchCount,
  validateMaxRetries,
  validateBackoffInterval,
} from '../../utils/validators';
import styles from './PropertyPanel.module.css';

// Aliases for backward-compatible internal usage
const validateDuration = validateWaitDuration;
const validateBranchCount = validateParallelBranchCount;

// --- Sub-components for each state type ---

export interface PropertyPanelProps {
  selectedState: WorkflowState | null;
  onConfigChange: (stateId: string, config: StateConfig) => void;
  onRetryPolicyChange: (stateId: string, policy: RetryPolicy | undefined) => void;
  contextVariables?: ContextVariable[];
  onContextVariablesChange?: (variables: ContextVariable[]) => void;
  /** Callback to request deletion of the selected state */
  onDeleteState?: (stateId: string) => void;
}

function ApiCallForm({
  config,
  onChange,
}: {
  config: ApiCallConfig;
  onChange: (config: ApiCallConfig) => void;
}) {
  const [errors, setErrors] = useState<Record<string, string | null>>({});

  const handleMethodChange = (method: HttpMethod) => {
    onChange({ ...config, method });
  };

  const handleUrlChange = (url: string) => {
    onChange({ ...config, url });
  };

  const handleBodyChange = (body: string) => {
    onChange({ ...config, body });
  };

  const handleTimeoutChange = (value: string) => {
    const num = parseInt(value, 10);
    if (!isNaN(num)) {
      const error = validateApiTimeout(num);
      setErrors((prev) => ({ ...prev, timeout: error }));
      onChange({ ...config, timeout: num });
    }
  };

  const handleHeaderAdd = () => {
    const newHeaders = { ...config.headers, '': '' };
    onChange({ ...config, headers: newHeaders });
  };

  const handleHeaderChange = (oldKey: string, newKey: string, value: string) => {
    const entries = Object.entries(config.headers).map(([k, v]) =>
      k === oldKey ? [newKey, value] : [k, v]
    );
    onChange({ ...config, headers: Object.fromEntries(entries) });
  };

  const handleHeaderRemove = (key: string) => {
    const newHeaders = { ...config.headers };
    delete newHeaders[key];
    onChange({ ...config, headers: newHeaders });
  };

  const handleMappingAdd = () => {
    const newMapping = { ...config.responseMapping, '': '' };
    onChange({ ...config, responseMapping: newMapping });
  };

  const handleMappingChange = (oldKey: string, newKey: string, value: string) => {
    const entries = Object.entries(config.responseMapping).map(([k, v]) =>
      k === oldKey ? [newKey, value] : [k, v]
    );
    onChange({ ...config, responseMapping: Object.fromEntries(entries) });
  };

  const handleMappingRemove = (key: string) => {
    const newMapping = { ...config.responseMapping };
    delete newMapping[key];
    onChange({ ...config, responseMapping: newMapping });
  };

  return (
    <div>
      <div className={styles.fieldGroup}>
        <label htmlFor="api-method" className={styles.fieldLabel}>Method</label>
        <select
          id="api-method"
          value={config.method}
          onChange={(e) => handleMethodChange(e.target.value as HttpMethod)}
          className={styles.input}
          data-testid="api-method-select"
        >
          <option value="GET">GET</option>
          <option value="POST">POST</option>
          <option value="PUT">PUT</option>
          <option value="PATCH">PATCH</option>
          <option value="DELETE">DELETE</option>
        </select>
      </div>

      <div className={styles.fieldGroup}>
        <label htmlFor="api-url" className={styles.fieldLabel}>URL</label>
        <input
          id="api-url"
          type="text"
          value={config.url}
          onChange={(e) => handleUrlChange(e.target.value)}
          className={styles.input}
          placeholder="https://api.example.com/endpoint"
          data-testid="api-url-input"
        />
      </div>

      <div className={styles.fieldGroup} role="group" aria-labelledby="api-headers-label">
        <label id="api-headers-label" className={styles.fieldLabel}>Headers</label>
        {Object.entries(config.headers).map(([key, value]) => (
          <div key={key} className={styles.keyValueRow}>
            <input
              type="text"
              value={key}
              onChange={(e) => handleHeaderChange(key, e.target.value, value)}
              className={styles.keyValueInput}
              placeholder="Key"
              aria-label="Header key"
            />
            <input
              type="text"
              value={value}
              onChange={(e) => handleHeaderChange(key, key, e.target.value)}
              className={styles.keyValueInput}
              placeholder="Value"
              aria-label="Header value"
            />
            <button
              className={styles.removeButton}
              onClick={() => handleHeaderRemove(key)}
              type="button"
              aria-label={`Remove header ${key || 'entry'}`}
            >
              ×
            </button>
          </div>
        ))}
        <button className={styles.button} onClick={handleHeaderAdd} type="button">
          + Add Header
        </button>
      </div>

      <div className={styles.fieldGroup}>
        <label htmlFor="api-body" className={styles.fieldLabel}>Body</label>
        <textarea
          id="api-body"
          value={config.body}
          onChange={(e) => handleBodyChange(e.target.value)}
          className={styles.textarea}
          placeholder='{"key": "value"}'
          data-testid="api-body-input"
        />
      </div>

      <div className={styles.fieldGroup} role="group" aria-labelledby="api-response-mapping-label">
        <label id="api-response-mapping-label" className={styles.fieldLabel}>Response Mapping</label>
        {Object.entries(config.responseMapping).map(([key, value]) => (
          <div key={key} className={styles.keyValueRow}>
            <input
              type="text"
              value={key}
              onChange={(e) => handleMappingChange(key, e.target.value, value)}
              className={styles.keyValueInput}
              placeholder="Variable"
              aria-label="Mapping variable"
            />
            <input
              type="text"
              value={value}
              onChange={(e) => handleMappingChange(key, key, e.target.value)}
              className={styles.keyValueInput}
              placeholder="JSON Path"
              aria-label="Mapping JSON path"
            />
            <button
              className={styles.removeButton}
              onClick={() => handleMappingRemove(key)}
              type="button"
              aria-label={`Remove mapping ${key || 'entry'}`}
            >
              ×
            </button>
          </div>
        ))}
        <button className={styles.button} onClick={handleMappingAdd} type="button">
          + Add Mapping
        </button>
      </div>

      <div className={styles.fieldGroup}>
        <label htmlFor="api-timeout" className={styles.fieldLabel}>Timeout (seconds)</label>
        <input
          id="api-timeout"
          type="number"
          value={config.timeout}
          onChange={(e) => handleTimeoutChange(e.target.value)}
          className={errors.timeout ? styles.inputError : styles.input}
          min={1}
          max={120}
          data-testid="api-timeout-input"
        />
        {errors.timeout && <div className={styles.errorText}>{errors.timeout}</div>}
      </div>
    </div>
  );
}

function ConditionForm({
  config,
  onChange,
}: {
  config: ConditionConfig;
  onChange: (config: ConditionConfig) => void;
}) {
  return (
    <div className={styles.fieldGroup}>
      <label htmlFor="condition-expression" className={styles.fieldLabel}>Expression</label>
      <input
        id="condition-expression"
        type="text"
        value={config.expression}
        onChange={(e) => onChange({ ...config, expression: e.target.value })}
        className={styles.input}
        placeholder='age > 18 AND status == "active"'
        data-testid="condition-expression-input"
      />
    </div>
  );
}

function ResponseForm({
  config,
  onChange,
}: {
  config: ResponseConfig;
  onChange: (config: ResponseConfig) => void;
}) {
  return (
    <div className={styles.fieldGroup}>
      <label htmlFor="response-template" className={styles.fieldLabel}>Message Template</label>
      <textarea
        id="response-template"
        value={config.messageTemplate}
        onChange={(e) => onChange({ ...config, messageTemplate: e.target.value })}
        className={styles.textareaLarge}
        placeholder="Hello {{userName}}, your order {{orderId}} is confirmed."
        data-testid="response-template-input"
      />
      <div className={styles.hint}>
        Use {'{{variableName}}'} to reference context variables
      </div>
    </div>
  );
}

function InputForm({
  config,
  onChange,
}: {
  config: InputConfig;
  onChange: (config: InputConfig) => void;
}) {
  const [errors, setErrors] = useState<Record<string, string | null>>({});

  const handleVariableNameChange = (variableName: string) => {
    const error = variableName ? validateVariableName(variableName) : null;
    setErrors((prev) => ({ ...prev, variableName: error }));
    onChange({ ...config, variableName });
  };

  const handleTimeoutChange = (value: string) => {
    const num = parseInt(value, 10);
    if (!isNaN(num)) {
      onChange({ ...config, timeout: num });
    }
  };

  return (
    <div>
      <div className={styles.fieldGroup}>
        <label htmlFor="input-prompt" className={styles.fieldLabel}>Prompt</label>
        <textarea
          id="input-prompt"
          value={config.prompt}
          onChange={(e) => onChange({ ...config, prompt: e.target.value })}
          className={styles.textarea}
          placeholder="Please enter your name:"
          data-testid="input-prompt-input"
        />
      </div>

      <div className={styles.fieldGroup}>
        <label htmlFor="input-variable" className={styles.fieldLabel}>Variable Name</label>
        <input
          id="input-variable"
          type="text"
          value={config.variableName}
          onChange={(e) => handleVariableNameChange(e.target.value)}
          className={errors.variableName ? styles.inputError : styles.input}
          placeholder="user_response"
          data-testid="input-variable-input"
          data-role="variable-name"
        />
        {errors.variableName && <div className={styles.errorText}>{errors.variableName}</div>}
      </div>

      <div className={styles.fieldGroup}>
        <label htmlFor="input-timeout" className={styles.fieldLabel}>Timeout (seconds)</label>
        <input
          id="input-timeout"
          type="number"
          value={config.timeout}
          onChange={(e) => handleTimeoutChange(e.target.value)}
          className={styles.input}
          min={1}
          data-testid="input-timeout-input"
        />
      </div>
    </div>
  );
}

function WaitForm({
  config,
  onChange,
}: {
  config: WaitConfig;
  onChange: (config: WaitConfig) => void;
}) {
  const [error, setError] = useState<string | null>(null);

  const handleDurationChange = (value: string) => {
    const num = parseInt(value, 10);
    if (!isNaN(num)) {
      const err = validateDuration(num);
      setError(err);
      onChange({ ...config, duration: num });
    }
  };

  return (
    <div className={styles.fieldGroup}>
      <label htmlFor="wait-duration" className={styles.fieldLabel}>Duration (seconds)</label>
      <input
        id="wait-duration"
        type="number"
        value={config.duration}
        onChange={(e) => handleDurationChange(e.target.value)}
        className={error ? styles.inputError : styles.input}
        min={1}
        max={86400}
        data-testid="wait-duration-input"
      />
      {error && <div className={styles.errorText}>{error}</div>}
    </div>
  );
}

function ParallelForm({
  config,
  onChange,
}: {
  config: ParallelConfig;
  onChange: (config: ParallelConfig) => void;
}) {
  const branchError = validateBranchCount(config.branches.length);

  const handleAddBranch = () => {
    if (config.branches.length >= 10) return;
    const newBranch: ParallelBranch = {
      id: crypto.randomUUID(),
      name: `Branch ${config.branches.length + 1}`,
      stateIds: [],
    };
    onChange({ ...config, branches: [...config.branches, newBranch] });
  };

  const handleRemoveBranch = (id: string) => {
    onChange({ ...config, branches: config.branches.filter((b) => b.id !== id) });
  };

  const handleBranchNameChange = (id: string, name: string) => {
    onChange({
      ...config,
      branches: config.branches.map((b) => (b.id === id ? { ...b, name } : b)),
    });
  };

  return (
    <div>
      <div className={styles.fieldGroup} role="group" aria-labelledby="parallel-branches-label">
        <label id="parallel-branches-label" className={styles.fieldLabel}>
          Branches ({config.branches.length})
        </label>
        {branchError && <div className={styles.errorText}>{branchError}</div>}
        {config.branches.map((branch) => (
          <div key={branch.id} className={styles.branchRow}>
            <input
              type="text"
              value={branch.name}
              onChange={(e) => handleBranchNameChange(branch.id, e.target.value)}
              className={styles.branchInput}
              placeholder="Branch name"
              aria-label={`Branch name for ${branch.name || 'new branch'}`}
            />
            <button
              className={styles.removeButton}
              onClick={() => handleRemoveBranch(branch.id)}
              type="button"
              disabled={config.branches.length <= 2}
              aria-label={`Remove branch ${branch.name || 'entry'}`}
            >
              ×
            </button>
          </div>
        ))}
        <button
          className={styles.button}
          onClick={handleAddBranch}
          type="button"
          disabled={config.branches.length >= 10}
        >
          + Add Branch
        </button>
      </div>
    </div>
  );
}

function RetryPolicyForm({
  retryPolicy,
  onChange,
}: {
  retryPolicy: RetryPolicy | undefined;
  onChange: (policy: RetryPolicy | undefined) => void;
}) {
  const [errors, setErrors] = useState<Record<string, string | null>>({});
  const enabled = retryPolicy !== undefined;

  const handleToggle = () => {
    if (enabled) {
      onChange(undefined);
    } else {
      onChange({ maxRetries: 3, backoffIntervalSeconds: 5 });
    }
  };

  const handleMaxRetriesChange = (value: string) => {
    const num = parseInt(value, 10);
    if (!isNaN(num) && retryPolicy) {
      const error = validateMaxRetries(num);
      setErrors((prev) => ({ ...prev, maxRetries: error }));
      onChange({ ...retryPolicy, maxRetries: num });
    }
  };

  const handleBackoffChange = (value: string) => {
    const num = parseInt(value, 10);
    if (!isNaN(num) && retryPolicy) {
      const error = validateBackoffInterval(num);
      setErrors((prev) => ({ ...prev, backoff: error }));
      onChange({ ...retryPolicy, backoffIntervalSeconds: num });
    }
  };

  return (
    <div className={styles.retrySection}>
      <div className={styles.retryToggle}>
        <input
          type="checkbox"
          checked={enabled}
          onChange={handleToggle}
          id="retry-toggle"
          data-testid="retry-toggle"
        />
        <label htmlFor="retry-toggle" className={styles.retryToggleLabel}>
          Enable Retry Policy
        </label>
      </div>

      {enabled && retryPolicy && (
        <div>
          <div className={styles.fieldGroup}>
            <label htmlFor="retry-max-retries" className={styles.fieldLabel}>Max Retries (0-10)</label>
            <input
              id="retry-max-retries"
              type="number"
              value={retryPolicy.maxRetries}
              onChange={(e) => handleMaxRetriesChange(e.target.value)}
              className={errors.maxRetries ? styles.inputError : styles.input}
              min={0}
              max={10}
              data-testid="retry-max-input"
            />
            {errors.maxRetries && <div className={styles.errorText}>{errors.maxRetries}</div>}
          </div>

          <div className={styles.fieldGroup}>
            <label htmlFor="retry-backoff" className={styles.fieldLabel}>Backoff Interval (1-300s)</label>
            <input
              id="retry-backoff"
              type="number"
              value={retryPolicy.backoffIntervalSeconds}
              onChange={(e) => handleBackoffChange(e.target.value)}
              className={errors.backoff ? styles.inputError : styles.input}
              min={1}
              max={300}
              data-testid="retry-backoff-input"
            />
            {errors.backoff && <div className={styles.errorText}>{errors.backoff}</div>}
          </div>
        </div>
      )}
    </div>
  );
}

// --- Main PropertyPanel ---

export default function PropertyPanel({
  selectedState,
  onConfigChange,
  onRetryPolicyChange,
  contextVariables = [],
  onContextVariablesChange,
  onDeleteState,
}: PropertyPanelProps) {
  const handleConfigChange = useCallback(
    (config: StateConfig) => {
      if (selectedState) {
        onConfigChange(selectedState.id, config);
      }
    },
    [selectedState, onConfigChange]
  );

  const handleRetryPolicyChange = useCallback(
    (policy: RetryPolicy | undefined) => {
      if (selectedState) {
        onRetryPolicyChange(selectedState.id, policy);
      }
    },
    [selectedState, onRetryPolicyChange]
  );

  if (!selectedState) {
    return (
      <div className={styles.panel} data-testid="property-panel">
        {onContextVariablesChange ? (
          <ContextVariablesPanel
            contextVariables={contextVariables}
            onChange={onContextVariablesChange}
          />
        ) : (
          <div className={styles.emptyMessage}>
            Select a state to configure
          </div>
        )}
      </div>
    );
  }

  const renderConfigForm = () => {
    switch (selectedState.config.type) {
      case 'API_Call':
        return (
          <ApiCallForm
            config={selectedState.config as ApiCallConfig}
            onChange={handleConfigChange}
          />
        );
      case 'Condition':
        return (
          <ConditionForm
            config={selectedState.config as ConditionConfig}
            onChange={handleConfigChange}
          />
        );
      case 'Response':
        return (
          <ResponseForm
            config={selectedState.config as ResponseConfig}
            onChange={handleConfigChange}
          />
        );
      case 'Input':
        return (
          <InputForm
            config={selectedState.config as InputConfig}
            onChange={handleConfigChange}
          />
        );
      case 'Wait':
        return (
          <WaitForm
            config={selectedState.config as WaitConfig}
            onChange={handleConfigChange}
          />
        );
      case 'Parallel':
        return (
          <ParallelForm
            config={selectedState.config as ParallelConfig}
            onChange={handleConfigChange}
          />
        );
      case 'End':
        return (
          <div className={styles.noConfig}>
            No configuration needed
          </div>
        );
      default:
        return null;
    }
  };

  return (
    <div className={styles.panel} data-testid="property-panel">
      <div className={styles.header}>
        <div>
          <div className={styles.stateName}>
            {selectedState.name}
          </div>
          <div className={styles.stateType}>
            Type: {selectedState.type}
          </div>
        </div>
        {onDeleteState && (
          <button
            onClick={() => onDeleteState(selectedState.id)}
            className={styles.deleteButton}
            data-testid="property-panel-delete-btn"
            title="Delete this state"
          >
            Delete
          </button>
        )}
      </div>

      <div className={styles.sectionTitle}>Configuration</div>
      {renderConfigForm()}

      {selectedState.config.type !== 'End' && (
        <RetryPolicyForm
          retryPolicy={selectedState.retryPolicy}
          onChange={handleRetryPolicyChange}
        />
      )}

      {onContextVariablesChange && (
        <ContextVariablesPanel
          contextVariables={contextVariables}
          onChange={onContextVariablesChange}
          collapsible
        />
      )}
    </div>
  );
}
