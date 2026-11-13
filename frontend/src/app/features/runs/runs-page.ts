import { HttpErrorResponse, httpResource } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { FormField, form, pattern, required, submit } from '@angular/forms/signals';
import { Router, RouterLink } from '@angular/router';

import { injectNow } from '../../core/clock';
import { API, RunsApi } from '../../core/runs-api';
import { Run, isTerminal, toneOf } from '../../core/run.model';
import { elapsed, formatDuration, relativeTime } from '../../core/time';
import { progressOf } from '../../core/timeline';
import { StatusBadge } from '../../shared/status-badge';

/** Same rules as the backend's RepositoryName and StartRunRequest. */
const REPOSITORY = /^[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})\/[A-Za-z0-9._-]{1,100}$/;
const BRANCH = /^(?!.*\.\.)(?![-/])[A-Za-z0-9._/-]{1,255}$/;
const REFRESH_MS = 2000;

type Filter = 'all' | 'active' | 'finished';

interface NewRun {
  repository: string;
  branch: string;
}

@Component({
  selector: 'bs-runs-page',
  imports: [FormField, RouterLink, StatusBadge],
  templateUrl: './runs-page.html',
  styleUrl: './runs-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RunsPage {
  private readonly api = inject(RunsApi);
  private readonly router = inject(Router);
  protected readonly now = injectNow(1000);

  protected readonly runs = httpResource<Run[]>(() => `${API}/runs?limit=50`, { defaultValue: [] });

  protected readonly filter = signal<Filter>('all');
  protected readonly stats = computed(() => {
    const runs = this.runs.value();
    return {
      total: runs.length,
      active: runs.filter((r) => !isTerminal(r.status)).length,
      opened: runs.filter((r) => r.status === 'PR_OPENED' || r.status === 'DRAFT').length,
      stopped: runs.filter((r) => ['FAILED', 'CANCELLED', 'NOT_ELIGIBLE'].includes(r.status)).length,
    };
  });
  protected readonly visible = computed(() => {
    const runs = this.runs.value();
    switch (this.filter()) {
      case 'active':
        return runs.filter((r) => !isTerminal(r.status));
      case 'finished':
        return runs.filter((r) => isTerminal(r.status));
      default:
        return runs;
    }
  });

  protected readonly model = signal<NewRun>({ repository: '', branch: '' });
  protected readonly newRun = form(this.model, (path) => {
    required(path.repository, { message: 'Enter a repository' });
    pattern(path.repository, REPOSITORY, { message: 'Use the GitHub owner/name form' });
    pattern(path.branch, BRANCH, { message: 'Not a valid branch name' });
  });
  protected readonly submitError = signal<string | null>(null);
  protected readonly examples = ['spring-projects/spring-petclinic', 'hsanjebri/demo-app'];

  protected readonly formatDuration = formatDuration;
  protected readonly relativeTime = relativeTime;
  protected readonly elapsed = elapsed;
  protected readonly progressOf = progressOf;
  protected readonly toneOf = toneOf;

  constructor() {
    // Keep the list fresh while anything is still running.
    effect((onCleanup) => {
      if (this.stats().active === 0) return;
      const timer = setInterval(() => this.runs.reload(), REFRESH_MS);
      onCleanup(() => clearInterval(timer));
    });
  }

  protected useExample(repository: string): void {
    this.model.update((m) => ({ ...m, repository }));
  }

  protected async start(event: Event): Promise<void> {
    event.preventDefault();
    this.submitError.set(null);
    await submit(this.newRun, async () => {
      const { repository, branch } = this.model();
      let runId: string;
      try {
        runId = (await this.api.start(repository.trim(), branch.trim() || null)).id;
      } catch (error) {
        this.submitError.set(describe(error));
        return undefined;
      }
      // The run exists now; a failed navigation must not be reported as a failed start.
      this.runs.reload();
      await this.router.navigate(['/runs', runId]);
      return undefined;
    });
  }
}

function describe(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    const detail = (error.error as { detail?: string } | null)?.detail;
    return detail ?? `Request failed (${error.status || 'network error'})`;
  }
  return 'Something went wrong';
}
