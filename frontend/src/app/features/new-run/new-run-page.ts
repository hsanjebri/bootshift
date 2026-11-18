import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormField, form, pattern, required, submit } from '@angular/forms/signals';
import { Router, RouterLink } from '@angular/router';

import { GitHub, GitHubRepo } from '../../core/github';
import { RunsApi } from '../../core/runs-api';
import { RunsStore } from '../../core/runs-store';
import { BRANCH_PATTERN, REPOSITORY_PATTERN } from '../../core/validation';
import { Icon, IconName } from '../../shared/icon';

interface NewRun {
  repository: string;
  branch: string;
}

interface Step {
  icon: IconName;
  title: string;
  body: string;
}

@Component({
  selector: 'bs-new-run-page',
  imports: [FormField, Icon, RouterLink],
  templateUrl: './new-run-page.html',
  styleUrl: './new-run-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NewRunPage {
  private readonly api = inject(RunsApi);
  private readonly router = inject(Router);
  private readonly store = inject(RunsStore);
  protected readonly github = inject(GitHub);

  protected readonly model = signal<NewRun>({ repository: '', branch: '' });
  protected readonly newRun = form(this.model, (path) => {
    required(path.repository, { message: 'Enter a repository' });
    pattern(path.repository, REPOSITORY_PATTERN, { message: 'Use the GitHub owner/name form' });
    pattern(path.branch, BRANCH_PATTERN, { message: 'Not a valid branch name' });
  });
  protected readonly submitError = signal<string | null>(null);

  protected readonly hasHistory = computed(() => this.store.runs.value().length > 0);

  /** Allowlisted repositories matching what is typed, when GitHub is connected. */
  protected readonly picks = computed(() => {
    const typed = this.model().repository.trim().toLowerCase();
    const allowed = this.github.allowed();
    const exact = allowed.some((r) => r.fullName.toLowerCase() === typed);
    return exact ? allowed : allowed.filter((r) => !typed || r.fullName.toLowerCase().includes(typed));
  });
  protected readonly selected = computed(() =>
    this.github.allowed().find((r) => r.fullName.toLowerCase() === this.model().repository.trim().toLowerCase()) ?? null,
  );

  /** Repositories from earlier runs, most recent first. Falls back to a public example. */
  protected readonly suggestions = computed(() => {
    const seen = new Set<string>();
    for (const run of this.store.runs.value()) {
      seen.add(run.repository);
      if (seen.size === 5) break;
    }
    return seen.size ? [...seen] : ['spring-projects/spring-petclinic'];
  });

  protected readonly steps: Step[] = [
    { icon: 'git', title: 'Clone', body: 'Short-lived token, removed before any build runs.' },
    { icon: 'clock', title: 'Baseline', body: 'Build and test the untouched project to know what already fails.' },
    { icon: 'search', title: 'Scan', body: 'Detect Boot and Java versions, modules and risks.' },
    { icon: 'bolt', title: 'Recipes', body: 'Run the pinned OpenRewrite migration recipes.' },
    { icon: 'terminal', title: 'Verify and repair', body: 'Rebuild; the agent fixes what still fails, within hard budgets.' },
    { icon: 'shield', title: 'Review', body: 'Deterministic guards check the diff, then a PR (or draft) opens.' },
  ];

  protected use(repository: string): void {
    this.model.update((m) => ({ ...m, repository }));
  }

  protected pick(repo: GitHubRepo): void {
    this.model.update((m) => ({ ...m, repository: repo.fullName }));
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
      this.store.refresh();
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
