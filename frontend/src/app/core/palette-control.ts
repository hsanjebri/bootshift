import { Injectable, signal } from '@angular/core';

/** Lets any component ask the single command palette to open. */
@Injectable({ providedIn: 'root' })
export class PaletteControl {
  private readonly requests = signal(0);
  /** Increments on every open request; the palette reacts to changes. */
  readonly openRequests = this.requests.asReadonly();

  open(): void {
    this.requests.update((n) => n + 1);
  }
}
