import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';

import { BackendHealth } from '../core/health';
import { PaletteControl } from '../core/palette-control';
import { RunsStore } from '../core/runs-store';
import { Theme } from '../core/theme';
import { Icon, IconName } from '../shared/icon';

interface NavItem {
  path: string;
  label: string;
  icon: IconName;
}

/** Sidebar, top bar and content area for every dashboard page. */
@Component({
  selector: 'bs-app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Icon],
  templateUrl: './app-shell.html',
  styleUrl: './app-shell.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppShell {
  private readonly router = inject(Router);
  protected readonly theme = inject(Theme);
  protected readonly store = inject(RunsStore);
  protected readonly health = inject(BackendHealth);
  protected readonly palette = inject(PaletteControl);

  protected readonly navOpen = signal(false);

  protected readonly nav: NavItem[] = [
    { path: '/overview', label: 'Overview', icon: 'overview' },
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
}
