import { DatePipe } from '@angular/common';
import { HttpErrorResponse, httpResource } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormField, form, pattern, required, submit } from '@angular/forms/signals';
import { Router, RouterLink } from '@angular/router';

import { API, RunsApi } from '../../core/runs-api';
import { Run, isTerminal, stateLabel } from '../../core/run.model';

/** Same rules as the backend's RepositoryName and StartRunRequest. */
const REPOSITORY = /^[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})\/[A-Za-z0-9._-]{1,100}$/;
const BRANCH = /^(?!.*\.\.)(?![-/])[A-Za-z0-9._/-]{1,255}$/;

interface NewRun {
  repository: string;
  branch: string;
}

@Component({
  selector: 'bs-runs-page',
  imports: [FormField, RouterLink, DatePipe],
  templateUrl: './runs-page.html',
  styleUrl: './runs-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RunsPage {
  private readonly api = inject(RunsApi);
  private readonly router = inject(Router);

  protected readonly runs = httpResource<Run[]>(() => `${API}/runs?limit=50`, { defaultValue: [] });

  protected readonly model = signal<NewRun>({ repository: '', branch: '' });
  protected readonly newRun = form(this.model, (path) => {
    required(path.repository, { message: 'Enter a repository' });
    pattern(path.repository, REPOSITORY, { message: 'Use the GitHub owner/name form' });
    pattern(path.branch, BRANCH, { message: 'Not a valid branch name' });
  });
  protected readonly submitError = signal<string | null>(null);

  protected readonly stateLabel = stateLabel;
  protected readonly isTerminal = isTerminal;

  protected async start(event: Event): Promise<void> {
    event.preventDefault();
    this.submitError.set(null);
    await submit(this.newRun, async () => {
      const { repository, branch } = this.model();
      try {
        const run = await this.api.start(repository.trim(), branch.trim() || null);
        await this.router.navigate(['/runs', run.id]);
      } catch (error) {
        this.submitError.set(describe(error));
      }
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
