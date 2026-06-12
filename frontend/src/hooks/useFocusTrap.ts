/**
 * Custom hook that traps keyboard focus within a container element.
 * When active, Tab/Shift+Tab cycles through focusable elements inside the container.
 * On open, focuses the first focusable element. On close, returns focus to the
 * previously active element (trigger).
 *
 * Requirements: 8.6
 */
import { useEffect } from 'react';

const FOCUSABLE_SELECTOR =
  'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])';

export function useFocusTrap(
  dialogRef: React.RefObject<HTMLElement | null>,
  isOpen: boolean
): void {
  useEffect(() => {
    if (!isOpen || !dialogRef.current) return;

    const dialog = dialogRef.current;
    const focusableElements = dialog.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR);
    const firstFocusable = focusableElements[0];

    // Store the element that triggered the dialog so we can return focus on close
    const triggerElement = document.activeElement as HTMLElement | null;

    // Move focus into the dialog
    firstFocusable?.focus();

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key !== 'Tab') return;

      // Re-query in case DOM changed (e.g. error messages appearing)
      const currentFocusable = dialog.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR);
      if (currentFocusable.length === 0) return;

      const first = currentFocusable[0];
      const last = currentFocusable[currentFocusable.length - 1];

      if (e.shiftKey && document.activeElement === first) {
        e.preventDefault();
        last.focus();
      } else if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault();
        first.focus();
      }
    };

    dialog.addEventListener('keydown', handleKeyDown);

    return () => {
      dialog.removeEventListener('keydown', handleKeyDown);
      // Return focus to the trigger element
      triggerElement?.focus();
    };
  }, [isOpen, dialogRef]);
}
