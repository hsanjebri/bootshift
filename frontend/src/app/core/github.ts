import { HttpClient, httpResource } from '@angular/common/http';
import { Injectable, computed, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { API } from './runs-api';

/** Mirrors GitHubApp.Status. */
export interface GitHubStatus {
  configured: boolean;
  appSlug: string | null;
  installUrl: string | null;
  installations: { id: number; account: string; accountType: string; repositorySelection: string }[];
  error: string | null;
}

/** Mirrors GitHubController.RepositoryView. */
export interface GitHubRepo {
  owner: string;
  name: string;
  fullName: string;
  isPrivate: boolean;
  archived: boolean;
  defaultBranch: string;
  htmlUrl: string;
  pushedAt: string | null;
  allowed: boolean;
}

/** GitHub connection status and the repositories the GitHub App can see. */
@Injectable({ providedIn: 'root' })
export class GitHub {
  private readonly http = inject(HttpClient);

  readonly status = httpResource<GitHubStatus>(() => `${API}/github/status`);
  readonly connected = computed(() => this.status.value()?.configured === true);

  /** Only fetched once a GitHub App is configured. */
  readonly repositories = httpResource<GitHubRepo[]>(() => (this.connected() ? `${API}/github/repositories` : undefined), {
    defaultValue: [],
  });
  readonly allowed = computed(() => this.repositories.value().filter((r) => r.allowed));

  async setAllowed(repo: GitHubRepo, allowed: boolean): Promise<GitHubRepo> {
    const url = `${API}/repositories/${encodeURIComponent(repo.owner)}/${encodeURIComponent(repo.name)}`;
    const updated = await firstValueFrom(this.http.put<GitHubRepo>(url, { allowed }));
    this.repositories.update((list) => (list ?? []).map((r) => (r.fullName === updated.fullName ? updated : r)));
    return updated;
  }

  refresh(): void {
    this.status.reload();
    this.repositories.reload();
  }
}
