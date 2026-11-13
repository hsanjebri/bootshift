import { TestBed } from '@angular/core/testing';

import { EventSourceFactory, RunEvents } from './run-events';

/** Just enough of EventSource to drive the service from a test. */
class FakeEventSource {
  static last: FakeEventSource;
  readonly listeners = new Map<string, (e: MessageEvent<string>) => void>();
  readyState = 0;
  closed = false;
  onopen: (() => void) | null = null;
  onerror: (() => void) | null = null;

  constructor(readonly url: string) {
    FakeEventSource.last = this;
  }

  addEventListener(type: string, listener: (e: MessageEvent<string>) => void) {
    this.listeners.set(type, listener);
  }

  close() {
    this.closed = true;
    this.readyState = 2;
  }

  emit(type: string, id: number, payload: Record<string, unknown>) {
    const data = JSON.stringify({ id, runId: 'run-1', type, payload, createdAt: '2026-11-13T00:00:00Z' });
    this.listeners.get(type)?.(new MessageEvent(type, { data }));
  }
}

describe('RunEvents', () => {
  let service: RunEvents;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [{ provide: EventSourceFactory, useValue: { create: (url: string) => new FakeEventSource(url) } }],
    });
    service = TestBed.inject(RunEvents);
  });

  it('connects to the run events endpoint', () => {
    service.connect('run-1');
    expect(FakeEventSource.last.url).toBe('/api/v1/runs/run-1/events');
  });

  it('collects events in order and reports the connection as live', () => {
    const stream = service.connect('run-1');
    FakeEventSource.last.onopen?.();
    FakeEventSource.last.emit('STATE_CHANGED', 1, { from: null, to: 'QUEUED' });
    FakeEventSource.last.emit('LOG', 2, { message: 'hello' });

    expect(stream.connection()).toBe('live');
    expect(stream.events().map((e) => e.id)).toEqual([1, 2]);
    expect(stream.finished()).toBe(false);
  });

  it('ignores events replayed twice after a reconnect', () => {
    const stream = service.connect('run-1');
    FakeEventSource.last.emit('LOG', 5, { message: 'once' });
    FakeEventSource.last.emit('LOG', 5, { message: 'once' });

    expect(stream.events()).toHaveLength(1);
  });

  it('closes the EventSource after the final state change', () => {
    const stream = service.connect('run-1');
    FakeEventSource.last.emit('STATE_CHANGED', 9, { from: 'REVIEW', to: 'PR_OPENED' });

    expect(stream.finished()).toBe(true);
    expect(stream.connection()).toBe('closed');
    expect(FakeEventSource.last.closed).toBe(true);
  });
});
