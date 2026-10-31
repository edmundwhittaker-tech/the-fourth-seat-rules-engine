"use client";

import { useId, useLayoutEffect, useRef, useState } from "react";
import { tableSurfaceOutline } from "../lib/table-surface";

/** Non-interactive stone layer; follows the existing grid rather than sizing it. */
export default function TableSurface({ mode }: { mode: "focused" | "overview" }) {
  const ref = useRef<SVGSVGElement>(null);
  const id = useId().replace(/[^a-zA-Z0-9_-]/g, "");
  const [surface, setSurface] = useState<ReturnType<typeof tableSurfaceOutline>>(null);
  useLayoutEffect(() => {
    const board = ref.current?.parentElement;
    const commander = board?.querySelector<HTMLElement>(".table-commander");
    if (!board || !commander) return;
    let frame = 0;
    const measure = () => {
      const outer = board.getBoundingClientRect();
      const wing = commander.getBoundingClientRect();
      const originX = outer.left + board.clientLeft;
      const originY = outer.top + board.clientTop;
      const next = tableSurfaceOutline({ width: board.clientWidth, height: board.clientHeight,
        commander: { left: wing.left - originX, right: wing.right - originX, top: wing.top - originY, bottom: wing.bottom - originY } });
      setSurface(previous => previous?.path === next?.path && previous?.width === next?.width && previous?.height === next?.height ? previous : next);
    };
    const schedule = () => { cancelAnimationFrame(frame); frame = requestAnimationFrame(measure); };
    measure();
    const observer = new ResizeObserver(schedule);
    observer.observe(board);
    observer.observe(commander);
    window.addEventListener("resize", schedule);
    return () => { observer.disconnect(); cancelAnimationFrame(frame); window.removeEventListener("resize", schedule); };
  }, [mode]);
  const outline = surface?.path;
  return <svg ref={ref} className="table-stone-surface" aria-hidden="true" focusable="false"
    viewBox={surface ? `0 0 ${surface.width} ${surface.height}` : undefined} preserveAspectRatio="none">
    <defs>
      <pattern id={`${id}-stone`} patternUnits="userSpaceOnUse" width="820" height="820">
        <rect width="820" height="820" fill="#141413" />
        <image href="/tabletop/smooth-charcoal-stone.png" width="820" height="820" opacity=".88" />
      </pattern>
      <linearGradient id={`${id}-light`} x1="0" y1="0" x2="0" y2="1">
        <stop stopColor="#000" stopOpacity="0" /><stop offset="1" stopColor="#000" stopOpacity=".12" />
      </linearGradient>
      <linearGradient id={`${id}-edge`} x1="0" y1="0" x2="0" y2="1">
        <stop stopColor="var(--table-edge-brass)" /><stop offset=".55" stopColor="#5d5038" stopOpacity=".7" /><stop offset="1" stopColor="#685a40" stopOpacity=".65" />
      </linearGradient>
    </defs>
    {outline ? <>
      <path className="table-stone-fill" d={outline} fill={`url(#${id}-stone)`} stroke="#070706" strokeWidth="1.5" />
      <path d={outline} fill={`url(#${id}-light)`} stroke={`url(#${id}-edge)`} strokeWidth="1" vectorEffect="non-scaling-stroke" />
    </> : <rect className="table-stone-fallback" width="100%" rx="15" fill={`url(#${id}-stone)`} />}
  </svg>;
}
