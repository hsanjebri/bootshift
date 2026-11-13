import { Injectable, Signal, computed, inject, signal } from '@angular/core';

import { API } from './runs-api';
import { RunEvent, RunState, isTerminal } from './run.model';

const EVENT_TYPES = ['STATE_CHANGED', 'LOG', 'CANCEL_REQUESTED', 'RECOVERED'] as const;
/** EventSource.CLOSED, inlined so the module also loads where EventSource is not defined (tests). */
const EVENT_SOURCE_CLOSED = 2;

export type ConnectionState = 'connecting' | 'live' | 'reconnecting' | 'closed';

/** A live view of one run's event stream, exposed as signals. */
export interface RunEventStream {
  readonly events: Signal<readonly RunEvent[]>;
  readonly connection: Signal<ConnectionState>;
  /** True once the final STATE_CHANGED event arrived; the stream is then closed. */
  readonly finished: Signal<boolean>;
  close(): void;
}

/** Factory for EventSource, replaceable in tests. */
@Injectable({ providedIn: 'root' })
export class EventSourceFactory {
  create(url: string): EventSource {
    return new EventSource(url);
  }
}

/**
 * Wraps the SSE endpoint in signals. The browser's EventSource resends Last-Event-ID when it
 * reconnects, and the server replays what was missed, so reconnects need no extra code here.
 */
@Injectable({ providedIn: 'root' })
export class RunEvents {
  private readonly sources = inject(EventSourceFactory);

  connect(runId: string): RunEventStream {
    const events = signal<readonly RunEvent[]>([]);
    const connection = signal<ConnectionState>('connecting');
    const finished = signal(false);
    const seen = new Set<number>();
    const source = this.sources.create(`${API}/runs/${encodeURIComponent(runId)}/events`);

    const close = () => {
      source.close();
      connection.set('closed');
    };

    source.onopen = () => connection.set('live');
    source.onerror = () => {
      if (!finished()) connection.set(source.readyState === EVENT_SOURCE_CLOSED ? 'closed' : 'reconnecting');
    };

    for (const type of EVENT_TYPES) {
      source.addEventListener(type, (message: MessageEvent<string>) => {
        const event = JSON.parse(message.data) as RunEvent;
        if (seen.has(event.id)) return;
        seen.add(event.id);
        events.update((list) => [...list, event]);
        if (event.type === 'STATE_CHANGED' && isTerminal(event.payload['to'] as RunState)) {
          finished.set(true);
          close();
        }
      });
    }

    return {
      events: events.asReadonly(),
      connection: connection.asReadonly(),
      finished: computed(() => finished()),
      close,
    };
  }
}
