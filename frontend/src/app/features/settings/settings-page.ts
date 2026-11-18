import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { GitHub } from '../../core/github';

import { BackendHealth } from '../../core/health';
import { Theme, ThemeChoice } from '../../core/theme';
import { Toasts } from '../../core/toasts';
import { GitHubConnect } from '../../shared/github-connect';
import { Icon, IconName } from '../../shared/icon';

@Component({
  selector: 'bs-settings-page',
  imports: [GitHubConnect, Icon, RouterLink],
  templateUrl: './settings-page.html',
  styleUrl: './settings-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SettingsPage {
  protected readonly theme = inject(Theme);
  protected readonly health = inject(BackendHealth);
  protected readonly github = inject(GitHub);
  private readonly toasts = inject(Toasts);
  private readonly router = inject(Router);

  /** Set to "failed" when GitHub's redirect could not be completed (?github=failed). */
  readonly githubResult = input<string | undefined>(undefined, { alias: 'github' });
  protected readonly failed = computed(() => this.githubResult() === 'failed');

  protected readonly confirmingDisconnect = signal(false);
  protected readonly disconnecting = signal(false);

  protected async disconnect(): Promise<void> {
    this.disconnecting.set(true);
    try {
      await this.github.disconnect();
      this.toasts.show({ tone: 'info', title: 'GitHub disconnected', body: 'Delete the app on GitHub too if you no longer need it.' });
      void this.router.navigate([], { queryParams: {}, replaceUrl: true });
    } catch {
      this.toasts.show({ tone: 'danger', title: 'Could not disconnect GitHub' });
    } finally {
      this.disconnecting.set(false);
      this.confirmingDisconnect.set(false);
    }
  }

  protected readonly themes: { id: ThemeChoice; label: string; icon: IconName }[] = [
    { id: 'system', label: 'System', icon: 'monitor' },
    { id: 'light', label: 'Light', icon: 'sun' },
    { id: 'dark', label: 'Dark', icon: 'moon' },
  ];

  protected readonly shortcuts = [
    { keys: ['Ctrl', 'K'], action: 'Open the command palette' },
    { keys: ['N'], action: 'New run' },
    { keys: ['/'], action: 'Search runs on the overview' },
    { keys: ['Esc'], action: 'Close dialogs' },
  ];

  protected readonly endpoints = [
    { method: 'POST', path: '/api/v1/runs', note: 'Start a run (202, Idempotency-Key)' },
    { method: 'GET', path: '/api/v1/runs', note: 'List runs' },
    { method: 'GET', path: '/api/v1/runs/{id}', note: 'Run details' },
    { method: 'POST', path: '/api/v1/runs/{id}/cancel', note: 'Cancel a run' },
    { method: 'GET', path: '/api/v1/runs/{id}/events', note: 'Live events (SSE)' },
  ];

  protected readonly mcpSnippet = `{
  "mcpServers": {
    "bootshift": {
      "type": "http",
      "url": "https://bootshift.example.com/mcp",
      "headers": { "X-API-Key": "\${BOOTSHIFT_API_KEY}" }
    }
  }
}`;
}
