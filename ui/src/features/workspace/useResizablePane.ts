import { useCallback, useEffect, useRef, useState } from 'react';

export interface ResizablePaneOptions {
  readonly defaultWidth: number;
  readonly storageKey?: string;
  /** Which way growing the pane moves the mouse: left pane grows when dragging right, right pane grows when dragging left. */
  readonly direction: 'grow-right' | 'grow-left';
}

function readStored(key: string | undefined, fallback: number): number {
  if (!key) return fallback;
  try {
    const raw = localStorage.getItem(key);
    const parsed = raw === null ? NaN : Number(raw);
    return Number.isFinite(parsed) ? parsed : fallback;
  } catch {
    return fallback;
  }
}

function writeStored(key: string | undefined, value: number): void {
  if (!key) return;
  try {
    localStorage.setItem(key, String(value));
  } catch {
    // Best-effort only; the pane still resizes for this session.
  }
}

/**
 * A pane whose width is dragged directly via a thin divider, not toggled by a
 * button. Below collapseThreshold (defaultWidth / 3) it snaps to 0; dragging
 * the same divider back out past that threshold restores it; the pane cannot
 * grow past maxWidth (defaultWidth * 1.5).
 */
export function useResizablePane({ defaultWidth, storageKey, direction }: ResizablePaneOptions) {
  const collapseThreshold = defaultWidth / 3;
  const maxWidth = defaultWidth * 1.5;
  const [width, setWidth] = useState(() => readStored(storageKey, defaultWidth));
  const dragState = useRef<{ startX: number; startWidth: number } | null>(null);
  const [dragging, setDragging] = useState(false);

  const onPointerMove = useCallback(
    (event: PointerEvent) => {
      if (!dragState.current) return;
      const delta = event.clientX - dragState.current.startX;
      const signedDelta = direction === 'grow-right' ? delta : -delta;
      const raw = Math.min(maxWidth, Math.max(0, dragState.current.startWidth + signedDelta));
      setWidth(raw < collapseThreshold ? 0 : raw);
    },
    [collapseThreshold, direction, maxWidth],
  );

  const endDrag = useCallback(() => {
    dragState.current = null;
    setDragging(false);
    document.body.style.cursor = '';
    document.body.style.userSelect = '';
  }, []);

  useEffect(() => {
    if (!dragging) return;
    window.addEventListener('pointermove', onPointerMove);
    window.addEventListener('pointerup', endDrag);
    return () => {
      window.removeEventListener('pointermove', onPointerMove);
      window.removeEventListener('pointerup', endDrag);
    };
  }, [dragging, endDrag, onPointerMove]);

  useEffect(() => {
    if (!dragging) writeStored(storageKey, width);
  }, [dragging, storageKey, width]);

  const startDrag = useCallback(
    (event: React.PointerEvent) => {
      event.preventDefault();
      dragState.current = { startX: event.clientX, startWidth: width };
      setDragging(true);
      document.body.style.cursor = 'col-resize';
      document.body.style.userSelect = 'none';
    },
    [width],
  );

  return { width, collapsed: width === 0, startDrag, dragging };
}
