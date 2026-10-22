import { useCallback, useEffect, useState } from "react";

export interface PollingState<T> {
  data: T | null;
  error: Error | null;
  loading: boolean;
  lastUpdated: Date | null;
  refresh: () => void;
}

export const DEFAULT_POLL_INTERVAL_MS = 3000;

/**
 * Calls `fn` immediately and then every `intervalMs`. Polling pauses while the tab is hidden and
 * resumes (with an immediate refresh) when it becomes visible again. An `intervalMs` of 0 fetches
 * once per `fn` identity (plus explicit `refresh()` calls) without an interval. `fn` must be
 * referentially stable (module-level or wrapped in useCallback) or polling restarts every render.
 */
export function usePolling<T>(
  fn: () => Promise<T>,
  intervalMs: number = DEFAULT_POLL_INTERVAL_MS,
  enabled = true,
): PollingState<T> {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<Error | null>(null);
  const [loading, setLoading] = useState<boolean>(enabled);
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);
  const [tick, setTick] = useState(0);

  useEffect(() => {
    if (!enabled) return undefined;
    let cancelled = false;
    // Scoped to this effect instance: StrictMode mounts effects twice and the first instance's
    // cancelled request must not block the second one from fetching.
    let inFlight = false;

    const run = async () => {
      if (document.hidden || inFlight) return;
      inFlight = true;
      try {
        const result = await fn();
        if (!cancelled) {
          setData(result);
          setError(null);
          setLastUpdated(new Date());
        }
      } catch (cause) {
        if (!cancelled) {
          setError(cause instanceof Error ? cause : new Error(String(cause)));
        }
      } finally {
        inFlight = false;
        if (!cancelled) setLoading(false);
      }
    };

    void run();
    const timer =
      intervalMs > 0
        ? window.setInterval(() => {
            void run();
          }, intervalMs)
        : undefined;
    const onVisibility = () => {
      if (!document.hidden) void run();
    };
    document.addEventListener("visibilitychange", onVisibility);

    return () => {
      cancelled = true;
      if (timer !== undefined) window.clearInterval(timer);
      document.removeEventListener("visibilitychange", onVisibility);
    };
  }, [fn, intervalMs, enabled, tick]);

  const refresh = useCallback(() => {
    setTick((t) => t + 1);
  }, []);

  return { data, error, loading, lastUpdated, refresh };
}
