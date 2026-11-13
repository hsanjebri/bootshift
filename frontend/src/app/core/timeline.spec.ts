import { RunEvent, RunState } from './run.model';
import { buildTimeline } from './timeline';

let nextId = 1;
function changed(to: RunState): RunEvent {
  return { id: nextId++, runId: 'r', type: 'STATE_CHANGED', payload: { to }, createdAt: '' };
}
function log(message: string): RunEvent {
  return { id: nextId++, runId: 'r', type: 'LOG', payload: { message }, createdAt: '' };
}
function statuses(events: RunEvent[]) {
  return Object.fromEntries(buildTimeline(events).steps.map((s) => [s.stage, s.status]));
}

describe('buildTimeline', () => {
  it('is all pending before any event', () => {
    const timeline = buildTimeline([]);
    expect(timeline.current).toBeNull();
    expect(timeline.result).toBeNull();
    expect(timeline.steps.every((s) => s.status === 'pending')).toBe(true);
  });

  it('marks earlier stages done and the latest one active', () => {
    const s = statuses([changed('QUEUED'), changed('CLONING'), log('cloning'), changed('BASELINE')]);
    expect(s['CLONING']).toBe('done');
    expect(s['BASELINE']).toBe('active');
    expect(s['SCANNING']).toBe('pending');
  });

  it('counts repeated VERIFY visits after a repair', () => {
    const timeline = buildTimeline(
      (['QUEUED', 'CLONING', 'BASELINE', 'SCANNING', 'RECIPE', 'VERIFY', 'REPAIR', 'VERIFY'] as RunState[]).map(changed),
    );
    expect(timeline.steps.find((s) => s.stage === 'VERIFY')?.visits).toBe(2);
    expect(timeline.steps.find((s) => s.stage === 'VERIFY')?.status).toBe('active');
  });

  it('shows every visited stage done when a PR was opened', () => {
    const timeline = buildTimeline(
      (['QUEUED', 'CLONING', 'BASELINE', 'SCANNING', 'RECIPE', 'VERIFY', 'REVIEW', 'PR_OPENED'] as RunState[]).map(changed),
    );
    expect(timeline.result).toBe('PR_OPENED');
    expect(timeline.steps.find((s) => s.stage === 'REVIEW')?.status).toBe('done');
    expect(timeline.steps.find((s) => s.stage === 'REPAIR')?.status).toBe('pending');
  });

  it('marks the stage where a cancelled or failed run stopped', () => {
    const s = statuses([changed('QUEUED'), changed('CLONING'), changed('BASELINE'), changed('CANCELLED')]);
    expect(s['CLONING']).toBe('done');
    expect(s['BASELINE']).toBe('stopped');
  });
});
