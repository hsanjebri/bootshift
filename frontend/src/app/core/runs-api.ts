import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { Run } from './run.model';

export const API = '/api/v1';

/** Writes to the runs API. Reads use httpResource in the components. */
@Injectable({ providedIn: 'root' })
export class RunsApi {
  private readonly http = inject(HttpClient);

  start(repository: string, branch: string | null): Promise<Run> {
    const body = branch ? { repository, branch } : { repository };
    return firstValueFrom(
      this.http.post<Run>(`${API}/runs`, body, {
        headers: { 'Idempotency-Key': crypto.randomUUID() },
      }),
    );
  }

  cancel(id: string): Promise<Run> {
    return firstValueFrom(this.http.post<Run>(`${API}/runs/${id}/cancel`, null));
  }
}
