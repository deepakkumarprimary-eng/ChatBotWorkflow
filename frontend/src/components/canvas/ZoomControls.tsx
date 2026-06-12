/**
 * Zoom and pan control panel for the workflow canvas.
 * Supports zoom range of 25% to 400% as specified in requirements.
 *
 * Requirements: 1.6 - Zoom controls (25%-400%) and pan controls
 */
import { useCallback } from 'react';
import { useReactFlow } from '@xyflow/react';
import styles from './ZoomControls.module.css';

export const MIN_ZOOM = 0.25;
export const MAX_ZOOM = 4.0;
const ZOOM_STEP = 0.25;

/**
 * Clamps a zoom value to the valid range [0.25, 4.0].
 * Values below 0.25 produce 0.25, values above 4.0 produce 4.0.
 */
export function clampZoom(zoom: number): number {
  if (zoom < MIN_ZOOM) return MIN_ZOOM;
  if (zoom > MAX_ZOOM) return MAX_ZOOM;
  return zoom;
}

export interface ZoomControlsProps {
  zoom: number;
}

export default function ZoomControls({ zoom }: ZoomControlsProps) {
  const { setViewport, getViewport, fitView } = useReactFlow();

  const handleZoomIn = useCallback(() => {
    const currentZoom = getViewport().zoom;
    const newZoom = clampZoom(currentZoom + ZOOM_STEP);
    const viewport = getViewport();
    setViewport({ ...viewport, zoom: newZoom });
  }, [getViewport, setViewport]);

  const handleZoomOut = useCallback(() => {
    const currentZoom = getViewport().zoom;
    const newZoom = clampZoom(currentZoom - ZOOM_STEP);
    const viewport = getViewport();
    setViewport({ ...viewport, zoom: newZoom });
  }, [getViewport, setViewport]);

  const handleFitView = useCallback(() => {
    fitView({ padding: 0.2 });
  }, [fitView]);

  const handleResetView = useCallback(() => {
    setViewport({ x: 0, y: 0, zoom: 1 });
  }, [setViewport]);

  const zoomPercentage = Math.round(zoom * 100);

  return (
    <div
      className={styles.container}
      data-testid="zoom-controls"
    >
      <button
        className={styles.button}
        onClick={handleZoomIn}
        disabled={zoom >= MAX_ZOOM}
        title="Zoom In"
        aria-label="Zoom In"
      >
        +
      </button>

      <div
        className={styles.zoomLevel}
        data-testid="zoom-level"
        aria-label={`Zoom level: ${zoomPercentage}%`}
      >
        {zoomPercentage}%
      </div>

      <button
        className={styles.button}
        onClick={handleZoomOut}
        disabled={zoom <= MIN_ZOOM}
        title="Zoom Out"
        aria-label="Zoom Out"
      >
        −
      </button>

      <hr className={styles.divider} />

      <button
        className={styles.button}
        onClick={handleFitView}
        title="Fit View"
        aria-label="Fit View"
      >
        ⊡
      </button>

      <button
        className={styles.button}
        onClick={handleResetView}
        title="Reset View"
        aria-label="Reset View"
      >
        ⌂
      </button>
    </div>
  );
}
