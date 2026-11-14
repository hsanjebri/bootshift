import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Toasts } from '../core/toasts';
import { Icon } from './icon';

@Component({
  selector: 'bs-toast-host',
  imports: [RouterLink, Icon],
  template: `
    <ol class="stack" aria-live="polite" aria-label="Notifications">
      @for (toast of toasts.list(); track toast.id) {
        <li class="toast" [attr.data-tone]="toast.tone">
          <bs-icon class="lead" [name]="toast.tone === 'success' ? 'check' : toast.tone === 'info' ? 'info' : 'alert'" />
          <div class="body">
            <strong>{{ toast.title }}</strong>
            @if (toast.body) {
              <span>{{ toast.body }}</span>
            }
            @if (toast.link; as link) {
              <a [routerLink]="link.commands" (click)="toasts.dismiss(toast.id)">{{ link.label }} →</a>
            }
          </div>
          <button type="button" class="btn icon sm" (click)="toasts.dismiss(toast.id)" aria-label="Dismiss">
            <bs-icon name="x" [size]="15" />
          </button>
        </li>
      }
    </ol>
  `,
  styles: `
    .stack {
      position: fixed;
      right: 1rem;
      bottom: 1rem;
      z-index: 50;
      display: grid;
      gap: 0.5rem;
      width: min(22rem, calc(100vw - 2rem));
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .toast {
      display: grid;
      grid-template-columns: auto 1fr auto;
      gap: 0.65rem;
      align-items: start;
      padding: 0.75rem 0.6rem 0.75rem 0.85rem;
      background: var(--surface-raised);
      border: 1px solid var(--border);
      border-left: 3px solid var(--tone, var(--accent));
      border-radius: 0.7rem;
      box-shadow: var(--shadow-lg);
      animation: slide-in 0.25s cubic-bezier(0.2, 0.8, 0.2, 1);
    }
    .toast[data-tone='success'] { --tone: var(--success); }
    .toast[data-tone='warning'] { --tone: var(--warning); }
    .toast[data-tone='danger'] { --tone: var(--danger); }
    .lead { color: var(--tone, var(--accent)); margin-top: 0.1rem; }
    .body { display: grid; gap: 0.15rem; font-size: 0.84rem; min-width: 0; }
    .body strong { font-weight: 600; overflow-wrap: anywhere; }
    .body span { color: var(--text-muted); }
    .body a { font-size: 0.8rem; font-weight: 600; text-decoration: none; width: fit-content; }
    @keyframes slide-in { from { opacity: 0; translate: 0 0.75rem; } }
    @media (prefers-reduced-motion: reduce) { .toast { animation: none; } }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ToastHost {
  protected readonly toasts = inject(Toasts);
}
