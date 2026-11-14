import { Run } from '../core/run.model';

export const RUNS_URL = '/api/v1/runs?limit=200';

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
