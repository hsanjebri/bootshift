import { httpResource } from '@angular/common/http';
import { Injectable, computed, effect, inject, untracked } from '@angular/core';

import { API } from './runs-api';
import { Run, RunState, isTerminal, stateLabel } from './run.model';
import { Toasts } from './toasts';

const FAST_MS = 2000;
const SLOW_MS = 15000;

/**
 * The runs list, shared by every page. Refreshes every 2s while anything is running and every
 * 15s otherwise, and raises a toast when a run finishes, whichever page is open.
 */
@Injectable({ providedIn: 'root' })
export class RunsStore {
  private readonly toasts = inject(Toasts);
  private readonly lastSeen = new Map<string, RunState>();

  readonly runs = httpResource<Run[]>(() => `${API}/runs?limit=200`, { defaultValue: [] });

  readonly active = computed(() => this.runs.value().filter((r) => !isTerminal(r.status)));
  readonly stats = computed(() => {
    const runs = this.runs.value();
    const finished = runs.filter((r) => isTerminal(r.status));
    const opened = runs.filter((r) => r.status === 'PR_OPENED' || r.status === 'DRAFT').length;
    return {
      total: runs.length,
      active: runs.length - finished.length,
      opened,
      stopped: runs.filter((r) => ['FAILED', 'CANCELLED', 'NOT_ELIGIBLE'].includes(r.status)).length,
      successRate: finished.length ? Math.round((opened / finished.length) * 100) : null,
    };
  });

  constructor() {
    effect((onCleanup) => {
      const interval = this.active().length > 0 ? FAST_MS : SLOW_MS;
      const timer = setInterval(() => this.runs.reload(), interval);
      onCleanup(() => clearInterval(timer));
    });
    effect(() => {
      const runs = this.runs.value();
      untracked(() => this.announceFinished(runs));
    });
  }

  refresh(): void {
    this.runs.reload();
  }

  /** Toast for runs that were seen running and are now finished. Silent on first load. */
  private announceFinished(runs: Run[]): void {
    const firstLoad = this.lastSeen.size === 0;
    for (const run of runs) {
      const before = this.lastSeen.get(run.id);
      this.lastSeen.set(run.id, run.status);
      if (firstLoad || !before || isTerminal(before) || !isTerminal(run.status)) continue;
      const tone = run.status === 'PR_OPENED' ? 'success' : run.status === 'DRAFT' ? 'warning' : run.status === 'CANCELLED' ? 'info' : 'danger';
      this.toasts.show({
        tone,
        title: `${run.repository}: ${stateLabel(run.status)}`,
        body: run.failureReason ?? undefined,
        link: { label: 'View run', commands: ['/runs', run.id] },
      });
    }
  }
}
