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
