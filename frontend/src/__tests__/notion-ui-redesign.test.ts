/**
 * Unit tests for Notion UI redesign token values and glassmorphism removal.
 *
 * Feature: notion-ui-redesign
 * Validates: Requirements 1.1–1.9, 1.11, 1.12
 */
import { describe, it, expect } from 'vitest';
import fs from 'fs';
import path from 'path';

const tokensPath = path.resolve(__dirname, '../styles/tokens.css');
const tokensCss = fs.readFileSync(tokensPath, 'utf-8');

// =============================================================================
// Token Values — Notion Palette (Requirements 1.1, 1.2, 1.4, 1.5)
// =============================================================================

describe('Design tokens contain expected Notion palette values', () => {
  it('defines --color-surface as #ffffff', () => {
    expect(tokensCss).toContain('--color-surface: #ffffff');
  });

  it('defines --color-surface-secondary as #f7f7f5', () => {
    expect(tokensCss).toContain('--color-surface-secondary: #f7f7f5');
  });

  it('defines --color-accent as #2383e2', () => {
    expect(tokensCss).toContain('--color-accent: #2383e2');
  });

  it('defines --color-text-primary as #2f3437', () => {
    expect(tokensCss).toContain('--color-text-primary: #2f3437');
  });

  it('defines --color-text-secondary as #6b6f76', () => {
    expect(tokensCss).toContain('--color-text-secondary: #6b6f76');
  });

  it('defines --color-border with rgba(0, 0, 0, 0.08)', () => {
    expect(tokensCss).toMatch(/--color-border:\s*rgba\(0,\s*0,\s*0,\s*0\.08\)/);
  });
});

// =============================================================================
// Glassmorphism Removal (Requirement 1.9)
// =============================================================================

describe('Design tokens do NOT contain glassmorphism tokens', () => {
  it('does not contain glass-bg', () => {
    expect(tokensCss).not.toMatch(/glass-bg/);
  });

  it('does not contain glass-blur', () => {
    expect(tokensCss).not.toMatch(/glass-blur/);
  });

  it('does not contain glass-border', () => {
    expect(tokensCss).not.toMatch(/glass-border/);
  });

  it('does not contain backdrop-filter', () => {
    expect(tokensCss).not.toMatch(/backdrop-filter/);
  });
});

// =============================================================================
// Neon Glow Token Removal (Requirement 1.9)
// =============================================================================

describe('Design tokens do NOT contain neon glow tokens', () => {
  it('does not contain glow-primary', () => {
    expect(tokensCss).not.toMatch(/glow-primary/);
  });

  it('does not contain glow-secondary', () => {
    expect(tokensCss).not.toMatch(/glow-secondary/);
  });

  it('does not contain glow-success', () => {
    expect(tokensCss).not.toMatch(/glow-success/);
  });

  it('does not contain glow-warning', () => {
    expect(tokensCss).not.toMatch(/glow-warning/);
  });

  it('does not contain glow-error', () => {
    expect(tokensCss).not.toMatch(/glow-error/);
  });
});

// =============================================================================
// Background Mesh Removal (Requirement 1.9)
// =============================================================================

describe('Design tokens do NOT contain background mesh', () => {
  it('does not contain --color-background-mesh', () => {
    expect(tokensCss).not.toMatch(/--color-background-mesh/);
  });
});

// =============================================================================
// React Flow CSS Variable Overrides (Requirements 1.11, 1.12)
// =============================================================================

describe('Design tokens contain .react-flow section with --xy-* overrides', () => {
  it('contains a .react-flow selector section', () => {
    expect(tokensCss).toMatch(/\.react-flow\s*\{/);
  });

  it('contains --xy-node-background-color-default', () => {
    expect(tokensCss).toContain('--xy-node-background-color-default');
  });

  it('contains --xy-edge-stroke-default', () => {
    expect(tokensCss).toContain('--xy-edge-stroke-default');
  });

  it('contains --xy-handle-background-color-default', () => {
    expect(tokensCss).toContain('--xy-handle-background-color-default');
  });

  it('contains --xy-selection-background-color-default', () => {
    expect(tokensCss).toContain('--xy-selection-background-color-default');
  });
});

// =============================================================================
// Transition Standard (Requirement 1.8)
// =============================================================================

describe('Design tokens define transition standard with ease-out', () => {
  it('contains --transition-standard with ease-out timing', () => {
    expect(tokensCss).toMatch(/--transition-standard:.*ease-out/);
  });
});
