/** Mirrors dev.bootshift.run.RunState. */
export type RunState =
  | 'QUEUED'
  | 'CLONING'
  | 'BASELINE'
  | 'SCANNING'
  | 'RECIPE'
  | 'VERIFY'
  | 'REPAIR'
  | 'REVIEW'
  | 'PR_OPENED'
  | 'DRAFT'
  | 'NOT_ELIGIBLE'
  | 'FAILED'
  | 'CANCELLED';

export const TERMINAL_STATES: ReadonlySet<RunState> = new Set([
  'PR_OPENED',
  'DRAFT',
  'NOT_ELIGIBLE',
  'FAILED',
  'CANCELLED',
]);

/** Pipeline stages in display order (README section 6). */
export const STAGES = ['CLONING', 'BASELINE', 'SCANNING', 'RECIPE', 'VERIFY', 'REPAIR', 'REVIEW'] as const;
export type Stage = (typeof STAGES)[number];

export const STAGE_LABELS: Record<Stage, string> = {
  CLONING: 'Clone',
  BASELINE: 'Baseline',
  SCANNING: 'Scan',
  RECIPE: 'Recipes',
  VERIFY: 'Verify',
  REPAIR: 'Repair',
  REVIEW: 'Review',
};

export const RESULT_LABELS: Partial<Record<RunState, string>> = {
  QUEUED: 'Queued',
  PR_OPENED: 'PR opened',
  DRAFT: 'Draft PR',
  NOT_ELIGIBLE: 'Not eligible',
  FAILED: 'Failed',
  CANCELLED: 'Cancelled',
};

/** Mirrors dev.bootshift.run.Run as serialized by the API. */
export interface Run {
  id: string;
  repository: string;
  status: RunState;
  stage: RunState | null;
  branch: string | null;
  prUrl: string | null;
  draft: boolean;
  failureReason: string | null;
  cancelRequested: boolean;
  createdAt: string;
  startedAt: string | null;
  finishedAt: string | null;
}

/** One SSE event from GET /api/v1/runs/{id}/events. */
export interface RunEvent {
  id: number;
  runId: string;
  type: 'STATE_CHANGED' | 'LOG' | 'CANCEL_REQUESTED' | 'RECOVERED' | string;
  payload: Record<string, unknown>;
  createdAt: string;
}

export function isTerminal(state: RunState): boolean {
  return TERMINAL_STATES.has(state);
}

export type Tone = 'live' | 'neutral' | 'success' | 'warning' | 'danger';

/** Colour family for a state. Green, amber and red are reserved for results. */
export function toneOf(state: RunState): Tone {
  switch (state) {
    case 'PR_OPENED':
      return 'success';
    case 'DRAFT':
      return 'warning';
    case 'FAILED':
    case 'NOT_ELIGIBLE':
      return 'danger';
    case 'CANCELLED':
    case 'QUEUED':
      return 'neutral';
    default:
      return 'live';
  }
}

export function stateLabel(state: RunState): string {
  return RESULT_LABELS[state] ?? STAGE_LABELS[state as Stage] ?? state;
}
