import { RunEvent, RunState, STAGES, Stage, isTerminal } from './run.model';

export type StepStatus = 'pending' | 'active' | 'done' | 'stopped';

export interface TimelineStep {
  stage: Stage;
  status: StepStatus;
  /** How many times the run entered this stage (VERIFY repeats after each repair). */
  visits: number;
}

export interface Timeline {
  steps: TimelineStep[];
  /** Latest state seen in the stream, or null before the first event. */
  current: RunState | null;
  /** Set once the run reached a terminal state. */
  result: RunState | null;
}

/**
 * How far along a run is, from 0 to 1, for progress bars. Uses the current state, or for a
 * finished run that did not succeed, the last stage it reached.
 */
export function progressOf(status: RunState, stage: RunState | null): number {
  if (status === 'PR_OPENED' || status === 'DRAFT') return 1;
  const reached = isTerminal(status) ? stage : status;
  const index = reached ? (STAGES as readonly string[]).indexOf(reached) : -1;
  return index < 0 ? 0 : (index + 1) / (STAGES.length + 1);
}

/** Builds the stage timeline from the STATE_CHANGED events of one run. Pure, so it is easy to test. */
export function buildTimeline(events: readonly RunEvent[]): Timeline {
  const visits = new Map<Stage, number>();
  let current: RunState | null = null;
  let lastStage: Stage | null = null;

  for (const event of events) {
    if (event.type !== 'STATE_CHANGED') continue;
    const to = event.payload['to'] as RunState;
    current = to;
    if ((STAGES as readonly string[]).includes(to)) {
      lastStage = to as Stage;
      visits.set(lastStage, (visits.get(lastStage) ?? 0) + 1);
    }
  }

  const result = current && isTerminal(current) ? current : null;
  const steps = STAGES.map((stage): TimelineStep => {
    const count = visits.get(stage) ?? 0;
    let status: StepStatus = count > 0 ? 'done' : 'pending';
    if (stage === lastStage && !result) status = 'active';
    if (stage === lastStage && result && result !== 'PR_OPENED' && result !== 'DRAFT') status = 'stopped';
    return { stage, status, visits: count };
  });

  return { steps, current, result };
}
