import { DOCUMENT } from '@angular/common';
import { Injectable, effect, inject, signal } from '@angular/core';

export type ThemeChoice = 'system' | 'light' | 'dark';

const STORAGE_KEY = 'bootshift.theme';
const ORDER: ThemeChoice[] = ['system', 'light', 'dark'];

/** Light, dark or follow the OS. Stored per browser; works without storage too. */
@Injectable({ providedIn: 'root' })
export class Theme {
  private readonly root = inject(DOCUMENT).documentElement;
  readonly choice = signal<ThemeChoice>(read());

  constructor() {
    effect(() => {
      const choice = this.choice();
      if (choice === 'system') delete this.root.dataset['theme'];
      else this.root.dataset['theme'] = choice;
      try {
        localStorage.setItem(STORAGE_KEY, choice);
      } catch {
        /* storage blocked: the choice still applies for this visit */
      }
    });
  }

  cycle(): void {
    this.choice.update((c) => ORDER[(ORDER.indexOf(c) + 1) % ORDER.length]);
  }
}

function read(): ThemeChoice {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    return ORDER.includes(stored as ThemeChoice) ? (stored as ThemeChoice) : 'system';
  } catch {
    return 'system';
  }
}
