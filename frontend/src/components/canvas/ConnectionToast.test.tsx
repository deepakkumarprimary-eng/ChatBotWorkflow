/**
 * Unit tests for the ConnectionToast component.
 * Validates rendering, auto-dismiss behavior, and accessibility attributes.
 *
 * Requirements: 1.4 - Display error messages when connections are rejected
 */
import { vi } from 'vitest';
import { render, screen, act } from '@testing-library/react';
import ConnectionToast, { ToastMessage } from './ConnectionToast';

describe('ConnectionToast', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('renders nothing when messages array is empty', () => {
    const { container } = render(
      <ConnectionToast messages={[]} onDismiss={() => {}} />
    );
    expect(container.querySelector('[data-testid="connection-toast-container"]')).toBeNull();
  });

  it('renders toast messages', () => {
    const messages: ToastMessage[] = [
      { id: '1', message: 'Self-loop transitions are not allowed' },
    ];
    render(<ConnectionToast messages={messages} onDismiss={() => {}} />);
    expect(screen.getByText('Self-loop transitions are not allowed')).toBeInTheDocument();
  });

  it('renders multiple toast messages', () => {
    const messages: ToastMessage[] = [
      { id: '1', message: 'Error one' },
      { id: '2', message: 'Error two' },
    ];
    render(<ConnectionToast messages={messages} onDismiss={() => {}} />);
    expect(screen.getByText('Error one')).toBeInTheDocument();
    expect(screen.getByText('Error two')).toBeInTheDocument();
  });

  it('has role="alert" for accessibility', () => {
    const messages: ToastMessage[] = [
      { id: '1', message: 'Test error' },
    ];
    render(<ConnectionToast messages={messages} onDismiss={() => {}} />);
    expect(screen.getByRole('alert')).toBeInTheDocument();
  });

  it('calls onDismiss after duration', () => {
    const onDismiss = vi.fn();
    const messages: ToastMessage[] = [
      { id: 'toast-1', message: 'Will dismiss' },
    ];
    render(<ConnectionToast messages={messages} onDismiss={onDismiss} duration={2000} />);

    // After 2000ms the fade starts, then 300ms later onDismiss is called
    act(() => {
      vi.advanceTimersByTime(2300);
    });

    expect(onDismiss).toHaveBeenCalledWith('toast-1');
  });
});
