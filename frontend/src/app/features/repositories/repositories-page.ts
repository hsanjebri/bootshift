import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { injectNow } from '../../core/clock';
import { GitHub, GitHubRepo } from '../../core/github';
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

/** GitHub repositories with their allowlist switch, plus every repository seen in runs. */
@Component({
  selector: 'bs-repositories-page',
  imports: [RouterLink, Icon, StatusBadge],
  templateUrl: './repositories-page.html',
  styleUrl: './repositories-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RepositoriesPage {
  /** Set by GitHub's post-install redirect (?installed=1). */
  readonly installed = input<string | undefined>();

  private readonly api = inject(RunsApi);
  private readonly router = inject(Router);
  private readonly toasts = inject(Toasts);
  protected readonly github = inject(GitHub);
  protected readonly store = inject(RunsStore);
  protected readonly now = injectNow(30000);

  protected readonly starting = signal<string | null>(null);
  protected readonly toggling = signal<string | null>(null);
  protected readonly search = signal('');

  protected readonly githubRepos = computed(() => {
    const q = this.search().trim().toLowerCase();
    return this.github.repositories.value().filter((r) => !q || r.fullName.toLowerCase().includes(q));
  });

  protected readonly fromRuns = computed<RepoSummary[]>(() => {
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

  constructor() {
    queueMicrotask(() => {
      if (this.installed()) {
        this.github.refresh();
        this.toasts.show({ tone: 'success', title: 'GitHub App installed', body: 'Allow the repositories Bootshift may work on.' });
        void this.router.navigate([], { queryParams: {}, replaceUrl: true });
      }
    });
  }

  protected rate(repo: RepoSummary): number | null {
    return repo.finished ? Math.round((repo.succeeded / repo.finished) * 100) : null;
  }

  protected async toggle(repo: GitHubRepo): Promise<void> {
    this.toggling.set(repo.fullName);
    try {
      const updated = await this.github.setAllowed(repo, !repo.allowed);
      this.toasts.show({
        tone: updated.allowed ? 'success' : 'info',
        title: updated.allowed ? `${updated.fullName} allowed` : `${updated.fullName} removed from the allowlist`,
      });
    } catch (error) {
      const detail = error instanceof HttpErrorResponse ? (error.error as { detail?: string } | null)?.detail : null;
      this.toasts.show({ tone: 'danger', title: 'Could not change the allowlist', body: detail ?? undefined });
    } finally {
      this.toggling.set(null);
    }
  }

  protected async runAgain(repo: RepoSummary): Promise<void> {
    this.starting.set(repo.name);
    try {
      const run = await this.api.start(repo.name, repo.latest.branch);
      this.store.refresh();
      await this.router.navigate(['/runs', run.id]);
    } catch (error) {
      const detail = error instanceof HttpErrorResponse ? (error.error as { detail?: string } | null)?.detail : null;
      this.toasts.show({ tone: 'danger', title: `Could not start a run for ${repo.name}`, body: detail ?? undefined });
    } finally {
      this.starting.set(null);
    }
  }
}
