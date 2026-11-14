import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { injectNow } from '../../core/clock';
import { RunsStore } from '../../core/runs-store';
import { STAGE_LABELS, Stage, isTerminal, toneOf } from '../../core/run.model';
import { elapsed, formatDuration, relativeTime } from '../../core/time';
import { progressOf } from '../../core/timeline';
import { Icon } from '../../shared/icon';
import { StatusBadge } from '../../shared/status-badge';

type Filter = 'all' | 'active' | 'finished';

/** Overview: summary, runs in progress, and the searchable list of all runs. */
@Component({
  selector: 'bs-runs-page',
  imports: [RouterLink, StatusBadge, Icon],
  templateUrl: './runs-page.html',
  styleUrl: './runs-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RunsPage {
  protected readonly store = inject(RunsStore);
  protected readonly now = injectNow(1000);

  protected readonly filter = signal<Filter>('all');
  protected readonly search = signal('');
  protected readonly filters: { id: Filter; label: string }[] = [
    { id: 'all', label: 'All' },
    { id: 'active', label: 'Active' },
    { id: 'finished', label: 'Finished' },
  ];

  protected readonly visible = computed(() => {
    const q = this.search().trim().toLowerCase();
    return this.store.runs.value().filter((r) => {
      if (q && !r.repository.toLowerCase().includes(q)) return false;
      if (this.filter() === 'active') return !isTerminal(r.status);
      if (this.filter() === 'finished') return isTerminal(r.status);
      return true;
    });
  });

  protected readonly formatDuration = formatDuration;
  protected readonly relativeTime = relativeTime;
  protected readonly elapsed = elapsed;
  protected readonly progressOf = progressOf;
  protected readonly toneOf = toneOf;

  protected stageName(stage: string): string {
    return STAGE_LABELS[stage as Stage] ?? 'Queued';
  }

  protected countFor(filter: Filter): number {
    const stats = this.store.stats();
    return filter === 'all' ? stats.total : filter === 'active' ? stats.active : stats.total - stats.active;
  }
}
