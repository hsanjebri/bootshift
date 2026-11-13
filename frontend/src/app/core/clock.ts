import { DestroyRef, Signal, inject, signal } from '@angular/core';

/** A signal holding Date.now(), refreshed every `intervalMs` until the injecting component is destroyed. */
export function injectNow(intervalMs = 1000): Signal<number> {
  const now = signal(Date.now());
  const timer = setInterval(() => now.set(Date.now()), intervalMs);
  inject(DestroyRef).onDestroy(() => clearInterval(timer));
  return now.asReadonly();
}
