import { ChangeDetectionStrategy, Component, computed, inject, signal, viewChild } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';

import { BackendHealth } from './core/health';
import { RunsStore } from './core/runs-store';
import { Theme } from './core/theme';
import { CommandPalette } from './shared/command-palette';
import { Icon, IconName } from './shared/icon';
import { ToastHost } from './shared/toast-host';

interface NavItem {
  path: string;
  label: string;
  icon: IconName;
  exact?: boolean;
}

@Component({
  selector: 'bs-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Icon, CommandPalette, ToastHost],
  templateUrl: './app.html',
  styleUrl: './app.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '(document:keydown)': 'onKeydown($event)',
  },
})
export class App {
  private readonly router = inject(Router);
  protected readonly theme = inject(Theme);
  protected readonly store = inject(RunsStore);
  protected readonly health = inject(BackendHealth);

  private readonly palette = viewChild.required(CommandPalette);
  protected readonly navOpen = signal(false);

  protected readonly nav: NavItem[] = [
    { path: '/', label: 'Overview', icon: 'overview', exact: true },
    { path: '/new', label: 'New run', icon: 'plus' },
    { path: '/repositories', label: 'Repositories', icon: 'repo' },
    { path: '/settings', label: 'Settings', icon: 'settings' },
  ];

  private readonly url = toSignal(
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      map((e) => e.urlAfterRedirects),
    ),
    { initialValue: this.router.url },
  );

  /** Breadcrumb for the top bar. */
  protected readonly crumbs = computed(() => {
    const url = this.url().split('?')[0];
    if (url.startsWith('/runs/')) return ['Overview', 'Run'];
    const item = this.nav.find((n) => n.path === url);
    return [item?.label ?? 'Overview'];
  });

  protected readonly themeIcon = computed<IconName>(() =>
    ({ system: 'monitor', light: 'sun', dark: 'moon' } as const)[this.theme.choice()],
  );

  constructor() {
    // Close the mobile drawer after navigating.
    this.router.events.pipe(filter((e) => e instanceof NavigationEnd)).subscribe(() => this.navOpen.set(false));
  }

  protected openPalette(): void {
    this.palette().open();
  }

  protected onKeydown(event: KeyboardEvent): void {
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
      event.preventDefault();
      this.openPalette();
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
