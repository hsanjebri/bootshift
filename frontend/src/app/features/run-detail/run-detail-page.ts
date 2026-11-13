import { DatePipe } from '@angular/common';
import { httpResource } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  afterRenderEffect,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { RouterLink } from '@angular/router';

import { injectNow } from '../../core/clock';
import { RunEventStream, RunEvents } from '../../core/run-events';
import { API, RunsApi } from '../../core/runs-api';
import { Run, RunEvent, RunState, STAGES, STAGE_LABELS, Stage, isTerminal, toneOf } from '../../core/run.model';
import { elapsed, formatDuration } from '../../core/time';
import { buildTimeline, progressOf } from '../../core/timeline';
import { StatusBadge } from '../../shared/status-badge';

/** A log line tagged with the stage the run was in when it was written. */
interface LogLine {
  id: number;
  at: string;
  stage: Stage | null;
  kind: 'log' | 'notice';
  text: string;
}

@Component({
  selector: 'bs-run-detail-page',
  imports: [RouterLink, DatePipe, StatusBadge],
  templateUrl: './run-detail-page.html',
  styleUrl: './run-detail-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RunDetailPage {
  /** Bound from the :id route parameter. */
  readonly id = input.required<string>();

  private readonly api = inject(RunsApi);
  private readonly runEvents = inject(RunEvents);
  protected readonly now = injectNow(1000);

  protected readonly run = httpResource<Run>(() => `${API}/runs/${encodeURIComponent(this.id())}`);
  protected readonly stream = signal<RunEventStream | null>(null);

  protected readonly events = computed(() => this.stream()?.events() ?? []);
  protected readonly timeline = computed(() => buildTimeline(this.events()));
  protected readonly logs = computed(() => toLogLines(this.events()));

  /** Prefer the live stream's state; fall back to the loaded run before the first event. */
  protected readonly state = computed<RunState | null>(() => this.timeline().current ?? this.run.value()?.status ?? null);
  protected readonly finished = computed(() => {
    const state = this.state();
    return state !== null && isTerminal(state);
  });
  protected readonly canCancel = computed(() => !this.finished() && this.state() !== null && !this.run.value()?.cancelRequested);
  protected readonly progress = computed(() => {
    const state = this.state();
    return state ? progressOf(state, this.lastStage()) : 0;
  });
  /** From the loaded run, or from the live stream before the run is reloaded. */
  protected readonly startedAt = computed(
    () => this.run.value()?.startedAt ?? this.stateChangeAt((to) => to === 'CLONING'),
  );
  protected readonly finishedAt = computed(
    () => this.run.value()?.finishedAt ?? this.stateChangeAt((to) => isTerminal(to)),
  );
  protected readonly duration = computed(() => formatDuration(elapsed(this.startedAt(), this.finishedAt(), this.now())));
  protected readonly tone = computed(() => toneOf(this.state() ?? 'QUEUED'));

  protected readonly cancelling = signal(false);
  protected readonly follow = signal(true);
  protected readonly copied = signal(false);

  protected readonly stageLabels = STAGE_LABELS;
  private readonly logPanel = viewChild<ElementRef<HTMLElement>>('logPanel');

  constructor() {
    // One stream per run id; closed when the id changes or the page is left.
    effect((onCleanup) => {
      const id = this.id();
      const stream = untracked(() => this.runEvents.connect(id));
      this.stream.set(stream);
      onCleanup(() => stream.close());
    });
    // Refresh the run details (finished time, final status) once the stream ends.
    effect(() => {
      if (this.stream()?.finished()) untracked(() => this.run.reload());
    });
    // Keep the newest log line in view while following.
    afterRenderEffect(() => {
      this.logs();
      const panel = this.logPanel()?.nativeElement;
      if (panel && untracked(this.follow)) panel.scrollTop = panel.scrollHeight;
    });
  }

  protected async cancel(): Promise<void> {
    this.cancelling.set(true);
    try {
      await this.api.cancel(this.id());
      this.run.reload();
    } finally {
      this.cancelling.set(false);
    }
  }

  protected async copyId(): Promise<void> {
    try {
      await navigator.clipboard.writeText(this.id());
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 1500);
    } catch {
      /* clipboard blocked; the id is still visible and selectable */
    }
  }

  protected onLogScroll(panel: HTMLElement): void {
    const atBottom = panel.scrollHeight - panel.scrollTop - panel.clientHeight < 24;
    this.follow.set(atBottom);
  }

  private stateChangeAt(match: (to: RunState) => boolean): string | null {
    const event = this.events().find((e) => e.type === 'STATE_CHANGED' && match(e.payload['to'] as RunState));
    return event?.createdAt ?? null;
  }

  private lastStage(): RunState | null {
    const steps = this.timeline().steps.filter((s) => s.visits > 0);
    return steps.length ? steps[steps.length - 1].stage : (this.run.value()?.stage ?? null);
  }
}

function toLogLines(events: readonly RunEvent[]): LogLine[] {
  const lines: LogLine[] = [];
  let stage: Stage | null = null;
  for (const event of events) {
    if (event.type === 'STATE_CHANGED') {
      const to = event.payload['to'] as RunState;
      if ((STAGES as readonly string[]).includes(to)) stage = to as Stage;
      continue;
    }
    const message = event.payload['message'];
    const text =
      typeof message === 'string'
        ? message
        : event.type === 'CANCEL_REQUESTED'
          ? 'Cancel requested; stopping at the next stage boundary'
          : JSON.stringify(event.payload);
    lines.push({ id: event.id, at: event.createdAt, stage, kind: event.type === 'LOG' ? 'log' : 'notice', text });
  }
  return lines;
}
