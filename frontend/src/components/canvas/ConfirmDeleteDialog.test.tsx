/**
 * Unit tests for the ConfirmDeleteDialog component.
 * Validates rendering, accessibility, and user interactions.
 *
 * Requirements: 1.7 - State deletion with confirmation
 */
import { vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import ConfirmDeleteDialog from './ConfirmDeleteDialog';

describe('ConfirmDeleteDialog', () => {
  const defaultProps = {
    visible: true,
    stateName: 'Fetch Data',
    transitionCount: 3,
    onConfirm: vi.fn(),
    onCancel: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders nothing when not visible', () => {
    render(<ConfirmDeleteDialog {...defaultProps} visible={false} />);
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('renders the dialog when visible', () => {
    render(<ConfirmDeleteDialog {...defaultProps} />);
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('displays the state name and transition count in the message', () => {
    render(<ConfirmDeleteDialog {...defaultProps} />);
    expect(
      screen.getByText(/Are you sure you want to delete "Fetch Data" and its 3 associated transition\(s\)\?/)
    ).toBeInTheDocument();
  });

  it('displays singular transition text for 1 transition', () => {
    render(<ConfirmDeleteDialog {...defaultProps} transitionCount={1} />);
    expect(
      screen.getByText(/1 associated transition/)
    ).toBeInTheDocument();
  });

  it('has proper accessibility attributes', () => {
    render(<ConfirmDeleteDialog {...defaultProps} />);
    const dialog = screen.getByRole('dialog');
    expect(dialog).toHaveAttribute('aria-modal', 'true');
    expect(dialog).toHaveAttribute('aria-labelledby', 'confirm-delete-title');
    expect(dialog).toHaveAttribute('aria-describedby', 'confirm-delete-message');
  });

  it('calls onConfirm when Delete button is clicked', () => {
    render(<ConfirmDeleteDialog {...defaultProps} />);
    fireEvent.click(screen.getByTestId('confirm-delete-confirm'));
    expect(defaultProps.onConfirm).toHaveBeenCalledTimes(1);
  });

  it('calls onCancel when Cancel button is clicked', () => {
    render(<ConfirmDeleteDialog {...defaultProps} />);
    fireEvent.click(screen.getByTestId('confirm-delete-cancel'));
    expect(defaultProps.onCancel).toHaveBeenCalledTimes(1);
  });

  it('calls onCancel when overlay is clicked', () => {
    render(<ConfirmDeleteDialog {...defaultProps} />);
    fireEvent.click(screen.getByTestId('confirm-delete-overlay'));
    expect(defaultProps.onCancel).toHaveBeenCalledTimes(1);
  });

  it('does not call onCancel when dialog body is clicked', () => {
    render(<ConfirmDeleteDialog {...defaultProps} />);
    fireEvent.click(screen.getByTestId('confirm-delete-dialog'));
    expect(defaultProps.onCancel).not.toHaveBeenCalled();
  });

  it('calls onCancel when Escape key is pressed', () => {
    render(<ConfirmDeleteDialog {...defaultProps} />);
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(defaultProps.onCancel).toHaveBeenCalledTimes(1);
  });

  it('displays the Delete State title', () => {
    render(<ConfirmDeleteDialog {...defaultProps} />);
    expect(screen.getByText('Delete State')).toBeInTheDocument();
  });

  it('displays Cancel and Delete buttons', () => {
    render(<ConfirmDeleteDialog {...defaultProps} />);
    expect(screen.getByText('Cancel')).toBeInTheDocument();
    expect(screen.getByText('Delete')).toBeInTheDocument();
  });
});
