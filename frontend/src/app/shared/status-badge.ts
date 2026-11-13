import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { RunState, isTerminal, stateLabel, toneOf } from '../core/run.model';

/** Status pill. Teal while running; green, amber and red only for results. */
@Component({
  selector: 'bs-status-badge',
  template: `<span class="dot" aria-hidden="true"></span>{{ label() }}`,
  host: {
    class: 'badge',
    '[attr.data-tone]': 'tone()',
    '[class.live]': 'live()',
  },
  styles: `
    :host {
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
      font-size: 0.75rem;
      font-weight: 600;
      letter-spacing: 0.01em;
      padding: 0.2rem 0.6rem 0.2rem 0.5rem;
      border-radius: 999px;
      background: var(--tone-bg);
      color: var(--tone-fg);
      white-space: nowrap;
      --tone-fg: var(--text-muted);
      --tone-bg: var(--chip);
    }
    :host([data-tone='live']) { --tone-fg: var(--accent); --tone-bg: var(--accent-soft); }
    :host([data-tone='success']) { --tone-fg: var(--success); --tone-bg: var(--success-soft); }
    :host([data-tone='warning']) { --tone-fg: var(--warning); --tone-bg: var(--warning-soft); }
    :host([data-tone='danger']) { --tone-fg: var(--danger); --tone-bg: var(--danger-soft); }
    .dot {
      width: 0.45rem;
      height: 0.45rem;
      border-radius: 50%;
      background: currentColor;
    }
    :host(.live) .dot { animation: blink 1.2s ease-in-out infinite; }
    @keyframes blink { 50% { opacity: 0.25; } }
    @media (prefers-reduced-motion: reduce) { :host(.live) .dot { animation: none; } }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StatusBadge {
  readonly state = input.required<RunState>();

  protected readonly label = computed(() => stateLabel(this.state()));
  protected readonly live = computed(() => !isTerminal(this.state()) && this.state() !== 'QUEUED');
  protected readonly tone = computed(() => toneOf(this.state()));
}
