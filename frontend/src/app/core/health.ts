import { httpResource } from '@angular/common/http';
import { Injectable, computed, effect } from '@angular/core';

export type BackendStatus = 'checking' | 'up' | 'down';

/** Polls the backend's readiness probe so the shell can show whether the API is reachable. */
@Injectable({ providedIn: 'root' })
export class BackendHealth {
  private readonly probe = httpResource<{ status: string }>(() => '/actuator/health/readiness');

  readonly status = computed<BackendStatus>(() => {
    if (this.probe.error()) return 'down';
    const value = this.probe.value();
    if (!value) return 'checking';
    return value.status === 'UP' ? 'up' : 'down';
  });

  constructor() {
    effect((onCleanup) => {
      const timer = setInterval(() => this.probe.reload(), this.status() === 'up' ? 15000 : 5000);
      onCleanup(() => clearInterval(timer));
    });
  }
}
