import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { Theme } from './core/theme';

@Component({
  selector: 'bs-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
  styleUrl: './app.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {
  protected readonly theme = inject(Theme);
  protected readonly themeLabel = computed(
    () => ({ system: 'Theme: system', light: 'Theme: light', dark: 'Theme: dark' })[this.theme.choice()],
  );
}
