import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { injectNow } from '../../core/clock';
import { RunsApi } from '../../core/runs-api';
import { RunsStore } from '../../core/runs-store';
import { Run, isTerminal } from '../../core/run.model';
import { relativeTime } from '../../core/time';
import { Toasts } from '../../core/toasts';
import { Icon } from '../../shared/icon';
import { StatusBadge } from '../../shared/status-badge';

interface RepoSummary {
  name: string;
  runs: number;
  latest: Run;
  succeeded: number;
  finished: number;
  running: boolean;
}

/** Repositories seen in runs. The real allowlist (with its own API) arrives in Phase 5. */
@Component({
  selector: 'bs-repositories-page',
  imports: [RouterLink, Icon, StatusBadge],
  templateUrl: './repositories-page.html',
  styleUrl: './repositories-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RepositoriesPage {
  private readonly api = inject(RunsApi);
  private readonly router = inject(Router);
  private readonly toasts = inject(Toasts);
  protected readonly store = inject(RunsStore);
  protected readonly now = injectNow(30000);
  protected readonly starting = signal<string | null>(null);

  protected readonly repos = computed<RepoSummary[]>(() => {
    const byName = new Map<string, Run[]>();
    for (const run of this.store.runs.value()) {
      byName.set(run.repository, [...(byName.get(run.repository) ?? []), run]);
    }
    return [...byName.entries()].map(([name, runs]) => {
      const finished = runs.filter((r) => isTerminal(r.status));
      return {
        name,
        runs: runs.length,
        latest: runs[0],
        finished: finished.length,
        succeeded: finished.filter((r) => r.status === 'PR_OPENED' || r.status === 'DRAFT').length,
        running: runs.some((r) => !isTerminal(r.status)),
      };
    });
  });

  protected readonly relativeTime = relativeTime;

  protected rate(repo: RepoSummary): number | null {
    return repo.finished ? Math.round((repo.succeeded / repo.finished) * 100) : null;
  }

  protected async runAgain(repo: RepoSummary): Promise<void> {
    this.starting.set(repo.name);
    try {
      const run = await this.api.start(repo.name, repo.latest.branch);
      this.store.refresh();
      await this.router.navigate(['/runs', run.id]);
    } catch {
      this.toasts.show({ tone: 'danger', title: `Could not start a run for ${repo.name}` });
    } finally {
      this.starting.set(null);
    }
  }
}
