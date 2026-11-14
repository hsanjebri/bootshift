import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { BackendHealth } from '../../core/health';
import { Theme, ThemeChoice } from '../../core/theme';
import { Icon, IconName } from '../../shared/icon';

@Component({
  selector: 'bs-settings-page',
  imports: [Icon],
  templateUrl: './settings-page.html',
  styleUrl: './settings-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SettingsPage {
  protected readonly theme = inject(Theme);
  protected readonly health = inject(BackendHealth);

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
