import { GitHubRepo, GitHubStatus } from '../core/github';
import { Run } from '../core/run.model';

export const RUNS_URL = '/api/v1/runs?limit=200';
export const STATUS_URL = '/api/v1/github/status';
export const REPOS_URL = '/api/v1/github/repositories';

export function gitHubStatus(configured: boolean): GitHubStatus {
  return {
    configured,
    appSlug: configured ? 'bootshift-test' : null,
    installUrl: configured ? 'https://github.com/apps/bootshift-test/installations/new' : null,
    installations: configured ? [{ id: 7, account: 'hsanjebri', accountType: 'User', repositorySelection: 'selected' }] : [],
    error: null,
  };
}

export function aGitHubRepo(overrides: Partial<GitHubRepo> = {}): GitHubRepo {
  const name = overrides.name ?? 'demo-app';
  return {
    owner: 'hsanjebri',
    name,
    fullName: `hsanjebri/${name}`,
    isPrivate: true,
    archived: false,
    defaultBranch: 'main',
    htmlUrl: `https://github.com/hsanjebri/${name}`,
    pushedAt: '2026-11-13T09:00:00Z',
    allowed: false,
    ...overrides,
  };
}

export function aRun(overrides: Partial<Run> = {}): Run {
  return {
    id: '6f1c2b1e-0000-4000-8000-000000000001',
    repository: 'hsanjebri/demo-app',
    status: 'PR_OPENED',
    stage: 'REVIEW',
    branch: null,
    prUrl: null,
    draft: false,
    failureReason: null,
    cancelRequested: false,
    createdAt: '2026-11-13T09:00:00Z',
    startedAt: '2026-11-13T09:00:01Z',
    finishedAt: '2026-11-13T09:00:09Z',
    ...overrides,
  };
}
