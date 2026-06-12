/**
 * Component Palette sidebar listing all available state types.
 * Users drag items from the palette onto the canvas to create new state instances.
 *
 * Requirements: 1.2 - Component palette with draggable state types
 * Requirements: 2.1 - Seven state types with default configurations
 * Requirements: 2.9 - Palette applies styles exclusively through CSS Module
 * Requirements: 3.3 - Palette items as cards with hover elevation transition
 * Requirements: 3.4 - Collapsible section header with chevron rotation
 */
import React, { useState } from 'react';
import { StateType } from '../../types/canvas.types';
import styles from './ComponentPalette.module.css';

/** DataTransfer type key used during drag-and-drop from palette to canvas */
export const DRAG_DATA_TYPE = 'application/workflow-state-type';

/** Color for each state type indicator */
const STATE_COLORS: Record<StateType, string> = {
  API_Call: '#1565c0',
  Condition: '#e65100',
  Response: '#2e7d32',
  Input: '#6a1b9a',
  Wait: '#f9a825',
  Parallel: '#00695c',
  End: '#b71c1c',
};

/** Display name for each state type */
const STATE_LABELS: Record<StateType, string> = {
  API_Call: 'API Call',
  Condition: 'Condition',
  Response: 'Response',
  Input: 'Input',
  Wait: 'Wait',
  Parallel: 'Parallel',
  End: 'End',
};

/** Brief description for each state type */
const STATE_DESCRIPTIONS: Record<StateType, string> = {
  API_Call: 'Make an HTTP request',
  Condition: 'Branch on a condition',
  Response: 'Send a message',
  Input: 'Wait for user input',
  Wait: 'Pause for a duration',
  Parallel: 'Run branches in parallel',
  End: 'End the workflow',
};

/** All state types in palette display order */
const STATE_TYPES: StateType[] = [
  'API_Call',
  'Condition',
  'Response',
  'Input',
  'Wait',
  'Parallel',
  'End',
];

export interface ComponentPaletteProps {
  /** Optional class name for the palette container */
  className?: string;
}

/**
 * Palette item representing a single draggable state type.
 */
function PaletteItem({ type }: { type: StateType }) {
  const handleDragStart = (e: React.DragEvent<HTMLDivElement>) => {
    e.dataTransfer.setData(DRAG_DATA_TYPE, type);
    e.dataTransfer.effectAllowed = 'move';
  };

  return (
    <div
      role="listitem"
      draggable
      onDragStart={handleDragStart}
      data-testid={`palette-item-${type}`}
      className={styles.item}
    >
      <div
        className={styles.colorIndicator}
        style={{ backgroundColor: STATE_COLORS[type] }}
      />
      <div className={styles.itemContent}>
        <div className={styles.itemLabel}>
          {STATE_LABELS[type]}
        </div>
        <div className={styles.itemDescription}>
          {STATE_DESCRIPTIONS[type]}
        </div>
      </div>
    </div>
  );
}

/**
 * ComponentPalette displays a sidebar with all available state types
 * that can be dragged onto the canvas.
 */
export default function ComponentPalette({ className }: ComponentPaletteProps) {
  const [expanded, setExpanded] = useState(true);

  return (
    <div
      className={`${styles.palette} ${className ?? ''}`}
      data-testid="component-palette"
      role="list"
      aria-label="Workflow state types"
    >
      <button
        className={styles.sectionHeader}
        onClick={() => setExpanded(!expanded)}
        aria-expanded={expanded}
        type="button"
      >
        <span className={`${styles.chevron} ${expanded ? styles.chevronExpanded : ''}`}>
          &#9654;
        </span>
        Components
      </button>
      {expanded && STATE_TYPES.map((type) => (
        <PaletteItem key={type} type={type} />
      ))}
    </div>
  );
}
