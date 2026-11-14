import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router, RouterOutlet } from '@angular/router';

import { PaletteControl } from './core/palette-control';
import { CommandPalette } from './shared/command-palette';
import { ToastHost } from './shared/toast-host';

/** Root: the routed page (landing or dashboard shell), plus app-wide palette, toasts and shortcuts. */
@Component({
  selector: 'bs-root',
  imports: [RouterOutlet, CommandPalette, ToastHost],
  template: `
    <a class="skip" href="#main">Skip to content</a>
    <router-outlet />
    <bs-command-palette />
    <bs-toast-host />
  `,
  styles: `
    .skip {
      position: absolute;
      left: -999px;
      top: 0.5rem;
      z-index: 60;
      background: var(--surface);
      color: var(--text);
      padding: 0.5rem 0.75rem;
      border-radius: 0.5rem;
    }
    .skip:focus { left: 0.75rem; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '(document:keydown)': 'onKeydown($event)',
  },
})
export class App {
  private readonly router = inject(Router);
  private readonly palette = inject(PaletteControl);

  protected onKeydown(event: KeyboardEvent): void {
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
      event.preventDefault();
      this.palette.open();
      return;
    }
    if (event.ctrlKey || event.metaKey || event.altKey || isTyping(event.target)) return;
    if (event.key === 'n') {
      event.preventDefault();
      void this.router.navigate(['/new']);
    } else if (event.key === '/') {
      const search = document.getElementById('run-search');
      if (search) {
        event.preventDefault();
        search.focus();
      }
    }
  }
}

function isTyping(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false;
  return target.isContentEditable || ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName) || !!target.closest('dialog[open]');
}
