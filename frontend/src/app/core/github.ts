import { HttpClient, httpResource } from '@angular/common/http';
import { Injectable, computed, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { API } from './runs-api';

/** Mirrors GitHubApp.Status. ENVIRONMENT: set with variables on the server; CONNECTED: made with Connect GitHub. */
export interface GitHubStatus {
  configured: boolean;
  source: 'ENVIRONMENT' | 'CONNECTED' | null;
  appSlug: string | null;
  installUrl: string | null;
  appUrl: string | null;
  installations: { id: number; account: string; accountType: string; repositorySelection: string }[];
  error: string | null;
}

/** Mirrors GitHubManifest.Form: where to post the manifest, and the manifest itself. */
export interface ManifestForm {
  action: string;
  manifest: string;
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

  /**
   * Starts the Connect GitHub flow: the backend describes the app, and the browser posts that
   * description to GitHub, which asks the user to confirm. GitHub then sends the browser back to
   * the backend, which saves the app and forwards to the install page.
   */
  async connect(organization: string | null, submit: (form: ManifestForm) => void = postToGitHub): Promise<void> {
    const form = await firstValueFrom(
      this.http.post<ManifestForm>(`${API}/github/manifest`, { dashboardUrl: location.origin, organization }),
    );
    submit(form);
  }

  /** Forgets the app saved by Connect GitHub. The app stays on GitHub until its owner deletes it there. */
  async disconnect(): Promise<void> {
    await firstValueFrom(this.http.delete<void>(`${API}/github/app`));
    this.refresh();
  }

  refresh(): void {
    this.status.reload();
    this.repositories.reload();
  }
}

/** GitHub only accepts the manifest as a form field in a top-level POST. */
function postToGitHub(form: ManifestForm): void {
  const el = document.createElement('form');
  el.method = 'post';
  el.action = form.action;
  const field = document.createElement('input');
  field.type = 'hidden';
  field.name = 'manifest';
  field.value = form.manifest;
  el.append(field);
  document.body.append(el);
  el.submit();
}
