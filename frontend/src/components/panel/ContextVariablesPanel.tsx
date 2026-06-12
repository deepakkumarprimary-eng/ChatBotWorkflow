/**
 * Context Variables Panel component.
 * Manages workflow-level context variables with inline validation.
 * Max 100 variables, names must match ^[a-zA-Z0-9_]{1,64}$.
 *
 * Requirements: 10.1, 10.4, 10.6
 */
import { useState, useCallback } from 'react';
import { ContextVariable } from '../../types/canvas.types';

// --- Constants ---

const MAX_VARIABLES = 100;
const VARIABLE_NAME_REGEX = /^[a-zA-Z0-9_]{1,64}$/;

// --- Validation ---

export function validateContextVariableName(name: string): string | null {
  if (!name) return 'Variable name is required';
  if (!VARIABLE_NAME_REGEX.test(name)) {
    return 'Must be 1-64 alphanumeric or underscore characters';
  }
  return null;
}

// --- Autocomplete utility ---

/**
 * Returns all variable names from the context variables list.
 * Can be used by other components to provide autocomplete suggestions.
 */
export function getAvailableVariableNames(contextVariables: ContextVariable[]): string[] {
  return contextVariables
    .map((v) => v.name)
    .filter((name) => name.length > 0);
}

// --- Styles ---

const sectionContainerStyle: React.CSSProperties = {
  borderTop: '1px solid #e0e0e0',
  paddingTop: '12px',
  marginTop: '16px',
};

const headerStyle: React.CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'space-between',
  marginBottom: '12px',
};

const titleStyle: React.CSSProperties = {
  fontSize: '14px',
  fontWeight: 600,
  color: '#333',
};

const countStyle: React.CSSProperties = {
  fontSize: '12px',
  color: '#888',
};

const variableRowStyle: React.CSSProperties = {
  display: 'flex',
  gap: '6px',
  marginBottom: '8px',
  alignItems: 'flex-start',
};

const inputStyle: React.CSSProperties = {
  padding: '6px 8px',
  border: '1px solid #ccc',
  borderRadius: '4px',
  fontSize: '13px',
  boxSizing: 'border-box',
};

const inputErrorStyle: React.CSSProperties = {
  ...inputStyle,
  borderColor: '#d32f2f',
};

const errorTextStyle: React.CSSProperties = {
  color: '#d32f2f',
  fontSize: '11px',
  marginTop: '2px',
};

const addButtonStyle: React.CSSProperties = {
  padding: '6px 12px',
  fontSize: '12px',
  border: '1px solid #ccc',
  borderRadius: '4px',
  backgroundColor: '#fff',
  cursor: 'pointer',
};

const addButtonDisabledStyle: React.CSSProperties = {
  ...addButtonStyle,
  opacity: 0.5,
  cursor: 'not-allowed',
};

const removeButtonStyle: React.CSSProperties = {
  padding: '6px 8px',
  fontSize: '12px',
  border: '1px solid #d32f2f',
  borderRadius: '4px',
  backgroundColor: '#fff',
  color: '#d32f2f',
  cursor: 'pointer',
  flexShrink: 0,
};

const collapsibleToggleStyle: React.CSSProperties = {
  background: 'none',
  border: 'none',
  cursor: 'pointer',
  fontSize: '12px',
  color: '#555',
  padding: '2px 4px',
};

// --- Component Props ---

export interface ContextVariablesPanelProps {
  contextVariables: ContextVariable[];
  onChange: (variables: ContextVariable[]) => void;
  /** Whether to render as a collapsible section (default: false) */
  collapsible?: boolean;
}

// --- Component ---

export default function ContextVariablesPanel({
  contextVariables,
  onChange,
  collapsible = false,
}: ContextVariablesPanelProps) {
  const [errors, setErrors] = useState<Record<number, string | null>>({});
  const [collapsed, setCollapsed] = useState(false);

  const atLimit = contextVariables.length >= MAX_VARIABLES;

  const handleAddVariable = useCallback(() => {
    if (atLimit) return;
    onChange([...contextVariables, { id: crypto.randomUUID(), name: '', defaultValue: '' }]);
  }, [contextVariables, onChange, atLimit]);

  const handleRemoveVariable = useCallback(
    (index: number) => {
      const updated = contextVariables.filter((_, i) => i !== index);
      onChange(updated);
      // Clear error for removed index
      setErrors((prev) => {
        const next = { ...prev };
        delete next[index];
        return next;
      });
    },
    [contextVariables, onChange]
  );

  const handleNameChange = useCallback(
    (index: number, name: string) => {
      let error = name ? validateContextVariableName(name) : null;
      // Check for duplicate names
      if (!error && name) {
        const isDuplicate = contextVariables.some(
          (v, i) => i !== index && v.name === name
        );
        if (isDuplicate) {
          error = 'Variable name already exists';
        }
      }
      setErrors((prev) => ({ ...prev, [index]: error }));
      const updated = contextVariables.map((v, i) =>
        i === index ? { ...v, name } : v
      );
      onChange(updated);
    },
    [contextVariables, onChange]
  );

  const handleDefaultValueChange = useCallback(
    (index: number, defaultValue: string) => {
      const updated = contextVariables.map((v, i) =>
        i === index ? { ...v, defaultValue } : v
      );
      onChange(updated);
    },
    [contextVariables, onChange]
  );

  const content = (
    <>
      {contextVariables.map((variable, index) => (
        <div key={variable.id}>
          <div style={variableRowStyle}>
            <div style={{ flex: 1 }}>
              <input
                type="text"
                value={variable.name}
                onChange={(e) => handleNameChange(index, e.target.value)}
                style={{
                  ...(errors[index] ? inputErrorStyle : inputStyle),
                  width: '100%',
                }}
                placeholder="variable_name"
                data-testid={`context-var-name-${index}`}
                aria-label={`Variable ${index + 1} name`}
                data-role="variable-name"
              />
              {errors[index] && (
                <div style={errorTextStyle} data-testid={`context-var-error-${index}`}>
                  {errors[index]}
                </div>
              )}
            </div>
            <div style={{ flex: 1 }}>
              <input
                type="text"
                value={variable.defaultValue as string}
                onChange={(e) => handleDefaultValueChange(index, e.target.value)}
                style={{ ...inputStyle, width: '100%' }}
                placeholder="default value"
                data-testid={`context-var-value-${index}`}
                aria-label={`Variable ${index + 1} default value`}
              />
            </div>
            <button
              style={removeButtonStyle}
              onClick={() => handleRemoveVariable(index)}
              type="button"
              data-testid={`context-var-remove-${index}`}
              aria-label={`Remove variable ${index + 1}`}
            >
              ×
            </button>
          </div>
        </div>
      ))}

      <button
        style={atLimit ? addButtonDisabledStyle : addButtonStyle}
        onClick={handleAddVariable}
        type="button"
        disabled={atLimit}
        data-testid="context-var-add"
        aria-label="Add context variable"
      >
        + Add Variable
      </button>
    </>
  );

  if (collapsible) {
    return (
      <div style={sectionContainerStyle} data-testid="context-variables-panel">
        <div style={headerStyle}>
          <div>
            <span style={titleStyle}>Workflow Variables</span>{' '}
            <span style={countStyle}>({contextVariables.length}/{MAX_VARIABLES})</span>
          </div>
          <button
            style={collapsibleToggleStyle}
            onClick={() => setCollapsed(!collapsed)}
            type="button"
            data-testid="context-var-toggle"
            aria-label={collapsed ? 'Expand variables' : 'Collapse variables'}
          >
            {collapsed ? '▶' : '▼'}
          </button>
        </div>
        {!collapsed && content}
      </div>
    );
  }

  return (
    <div data-testid="context-variables-panel">
      <div style={headerStyle}>
        <div>
          <span style={titleStyle}>Workflow Variables</span>{' '}
          <span style={countStyle}>({contextVariables.length}/{MAX_VARIABLES})</span>
        </div>
      </div>
      {contextVariables.length === 0 && (
        <div style={{ color: '#888', fontSize: '13px', marginBottom: '12px' }}>
          No variables defined. Add variables to pass data between states.
        </div>
      )}
      {content}
    </div>
  );
}
