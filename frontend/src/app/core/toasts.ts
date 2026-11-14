import { Injectable, signal } from '@angular/core';

export type ToastTone = 'info' | 'success' | 'warning' | 'danger';

export interface Toast {
  id: number;
  tone: ToastTone;
  title: string;
  body?: string;
  /** Optional router link shown as an action. */
  link?: { label: string; commands: unknown[] };
}

const DURATION_MS = 6000;
const MAX = 4;

/** Small, non-blocking notifications. Announced to screen readers by the host. */
@Injectable({ providedIn: 'root' })
export class Toasts {
  private nextId = 1;
  private readonly items = signal<readonly Toast[]>([]);
  readonly list = this.items.asReadonly();

  show(toast: Omit<Toast, 'id'>, durationMs = DURATION_MS): number {
    const id = this.nextId++;
    this.items.update((list) => [...list, { ...toast, id }].slice(-MAX));
    if (durationMs > 0) setTimeout(() => this.dismiss(id), durationMs);
    return id;
  }

  dismiss(id: number): void {
    this.items.update((list) => list.filter((t) => t.id !== id));
  }
}
