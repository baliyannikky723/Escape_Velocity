import { useEffect, useRef, useState, useCallback } from 'react';

interface UsePollingOptions {
  intervalMs?: number;
  enabled?: boolean;
  stopCondition?: () => boolean;
}

export function usePolling<T>(
  fetcher: () => Promise<T>,
  options: UsePollingOptions = {}
) {
  const { intervalMs = 3000, enabled = true, stopCondition } = options;
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const timerRef = useRef<number | null>(null);
  const isMountedRef = useRef<boolean>(true);

  const execute = useCallback(async (isInitial = false) => {
    if (isInitial) {
      setLoading(true);
    }
    try {
      const result = await fetcher();
      if (isMountedRef.current) {
        setData(result);
        setError(null);
      }
    } catch (err: any) {
      if (isMountedRef.current) {
        setError(err.response?.data?.message || err.message || 'Failed to fetch data');
      }
    } finally {
      if (isMountedRef.current && isInitial) {
        setLoading(false);
      }
    }
  }, [fetcher]);

  useEffect(() => {
    isMountedRef.current = true;
    if (!enabled) {
      setLoading(false);
      return;
    }

    execute(true);

    const scheduleNext = () => {
      if (!isMountedRef.current || !enabled) return;
      if (stopCondition && stopCondition()) return;

      timerRef.current = window.setTimeout(async () => {
        await execute(false);
        scheduleNext();
      }, intervalMs);
    };

    scheduleNext();

    return () => {
      isMountedRef.current = false;
      if (timerRef.current) {
        clearTimeout(timerRef.current);
      }
    };
  }, [enabled, intervalMs, execute, stopCondition]);

  const refetch = useCallback(() => execute(true), [execute]);

  return { data, loading, error, refetch };
}
