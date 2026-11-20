import { useSyncExternalStore } from 'react';
const reducedMotion = typeof matchMedia === 'function' ? matchMedia('(prefers-reduced-motion: reduce)') : null;
export function useReducedMotion(): boolean {
  return useSyncExternalStore(
    (onChange) => {
      reducedMotion?.addEventListener('change', onChange);
      return () => reducedMotion?.removeEventListener('change', onChange);
    },
    () => reducedMotion?.matches ?? false,
  );
}

/** Save-Data, or a cellular link where the browser says so (Network Information API, Chromium only). */
export function metered(): boolean {
  const connection = (navigator as Navigator & { connection?: { saveData?: boolean; type?: string } }).connection;
  return !!connection?.saveData || connection?.type === 'cellular';
}
