/**
 * Custom React Flow node component for rendering workflow states.
 * Each state type gets distinct visual styling for quick identification.
 *
 * Requirements: 1.1 - Render states as typed nodes with visual differentiation per StateType
 */
import { memo } from 'react';
import { Handle, Position, type NodeProps, type Node } from '@xyflow/react';
import { Globe, GitBranch, MessageSquare, FileInput, Clock, Layers, CircleStop, type LucideProps } from 'lucide-react';
import { StateType } from '../../../types/canvas.types';
import styles from './StateNode.module.css';

export type StateNodeData = {
  label: string;
  stateType: StateType;
  selected?: boolean;
  [key: string]: unknown;
};

/** Application-specific node type for state nodes */
export type StateNodeType = Node<StateNodeData, 'stateNode'>;

/** Icon mapping per state type — Lucide React line icons (Requirements 4.1–4.4) */
const STATE_TYPE_ICONS: Record<StateType, React.FC<LucideProps>> = {
  API_Call: Globe,
  Condition: GitBranch,
  Response: MessageSquare,
  Input: FileInput,
  Wait: Clock,
  Parallel: Layers,
  End: CircleStop,
};

/** Indicator colors for the left-border accent per state type (Requirement 2.7) */
const STATE_TYPE_INDICATOR: Record<StateType, string> = {
  API_Call: 'var(--color-indicator-blue)',
  Condition: 'var(--color-indicator-orange)',
  Response: 'var(--color-indicator-green)',
  Input: 'var(--color-indicator-purple)',
  Wait: 'var(--color-indicator-orange)',
  Parallel: 'var(--color-indicator-green)',
  End: 'var(--color-indicator-red)',
};

/** CSS module class mapping per state type */
const STATE_TYPE_CLASS: Record<StateType, string> = {
  API_Call: styles.apiCall,
  Condition: styles.condition,
  Response: styles.response,
  Input: styles.input,
  Wait: styles.wait,
  Parallel: styles.parallel,
  End: styles.end,
};

/** Handle CSS class mapping per state type */
const HANDLE_CLASS: Record<StateType, string> = {
  API_Call: styles.handleApiCall,
  Condition: styles.handleCondition,
  Response: styles.handleResponse,
  Input: styles.handleInput,
  Wait: styles.handleWait,
  Parallel: styles.handleParallel,
  End: styles.handleEnd,
};

function StateNode({ data, selected }: NodeProps<StateNodeType>) {
  const isEnd = data.stateType === 'End';
  const IconComponent = STATE_TYPE_ICONS[data.stateType];

  const nodeClasses = [
    styles.node,
    STATE_TYPE_CLASS[data.stateType],
    selected ? styles.selected : '',
    isEnd ? styles.endNode : '',
  ].filter(Boolean).join(' ');

  const handleClass = `${styles.handle} ${HANDLE_CLASS[data.stateType]}`;

  return (
    <div
      className={nodeClasses}
      style={{ '--node-indicator-color': STATE_TYPE_INDICATOR[data.stateType] } as React.CSSProperties}
      data-testid={`state-node-${data.stateType}`}
      data-node-type={data.stateType.toLowerCase().replace('_', '')}
      data-execution-state={selected ? 'selected' : 'idle'}
      aria-busy={false}
    >
      {/* Target handle (incoming transitions) — top */}
      <Handle
        type="target"
        position={Position.Top}
        id="target-top"
        className={handleClass}
      />

      {/* Target handle — left (alternative incoming connection point) */}
      <Handle
        type="target"
        position={Position.Left}
        id="target-left"
        className={handleClass}
      />

      <div className={styles.content}>
        <span className={styles.icon}>
          <IconComponent size={16} strokeWidth={1.5} />
        </span>
        <span className={styles.label}>
          {data.label}
        </span>
      </div>

      <div className={styles.type}>
        {data.stateType.replace('_', ' ')}
      </div>

      {/* Source handle (outgoing transitions) — bottom, not shown on End or Condition nodes */}
      {!isEnd && data.stateType !== 'Condition' && (
        <Handle
          type="source"
          position={Position.Bottom}
          id="source-bottom"
          className={handleClass}
        />
      )}

      {/* Source handle — right (alternative outgoing connection point) */}
      {!isEnd && data.stateType !== 'Condition' && (
        <Handle
          type="source"
          position={Position.Right}
          id="source-right"
          className={handleClass}
        />
      )}

      {/* Condition nodes get two source handles for true/false branches */}
      {data.stateType === 'Condition' && (
        <>
          <Handle
            type="source"
            position={Position.Left}
            id="false"
            className={`${styles.handle} ${styles.handleFalse}`}
          />
          <Handle
            type="source"
            position={Position.Right}
            id="true"
            className={`${styles.handle} ${styles.handleTrue}`}
          />
          <Handle
            type="source"
            position={Position.Bottom}
            id="source-bottom"
            className={handleClass}
          />
        </>
      )}
    </div>
  );
}

export default memo(StateNode);
