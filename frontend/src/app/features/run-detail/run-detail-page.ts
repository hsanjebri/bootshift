import { DatePipe } from '@angular/common';
import { httpResource } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
} from '@angular/core';
import { RouterLink } from '@angular/router';

import { RunEventStream, RunEvents } from '../../core/run-events';
import { API, RunsApi } from '../../core/runs-api';
import { Run, STAGE_LABELS, isTerminal, stateLabel } from '../../core/run.model';
import { buildTimeline } from '../../core/timeline';

@Component({
  selector: 'bs-run-detail-page',
  imports: [RouterLink, DatePipe],
  templateUrl: './run-detail-page.html',
  styleUrl: './run-detail-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RunDetailPage {
  /** Bound from the :id route parameter. */
  readonly id = input.required<string>();

  private readonly api = inject(RunsApi);
  private readonly runEvents = inject(RunEvents);

  protected readonly run = httpResource<Run>(() => `${API}/runs/${encodeURIComponent(this.id())}`);
  protected readonly stream = signal<RunEventStream | null>(null);

  protected readonly events = computed(() => this.stream()?.events() ?? []);
  protected readonly timeline = computed(() => buildTimeline(this.events()));
  protected readonly logs = computed(() =>
    this.events().filter((e) => e.type === 'LOG' || e.type === 'RECOVERED' || e.type === 'CANCEL_REQUESTED'),
  );
  /** Prefer the live stream's state; fall back to the loaded run before the first event. */
  protected readonly state = computed(() => this.timeline().current ?? this.run.value()?.status ?? null);
  protected readonly canCancel = computed(() => {
    const state = this.state();
    return state !== null && !isTerminal(state) && !this.run.value()?.cancelRequested;
  });
  protected readonly cancelling = signal(false);

  protected readonly stageLabels = STAGE_LABELS;
  protected readonly stateLabel = stateLabel;

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

  protected message(payload: Record<string, unknown>): string {
    const message = payload['message'];
    return typeof message === 'string' ? message : JSON.stringify(payload);
  }
}
