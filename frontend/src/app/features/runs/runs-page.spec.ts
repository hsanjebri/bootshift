import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { Run } from '../../core/run.model';
import { RunsPage } from './runs-page';

const RUN: Run = {
  id: '6f1c2b1e-0000-4000-8000-000000000001',
  repository: 'hsanjebri/demo-app',
  status: 'PR_OPENED',
  stage: 'REVIEW',
  branch: null,
  prUrl: null,
  draft: false,
  failureReason: null,
  cancelRequested: false,
  createdAt: '2026-11-13T09:00:00Z',
  startedAt: '2026-11-13T09:00:01Z',
  finishedAt: '2026-11-13T09:00:09Z',
};

describe('RunsPage', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [RunsPage],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  /** Renders the page and answers its initial list request (whenStable waits for pending HTTP). */
  async function render(runs: Run[]) {
    const fixture = TestBed.createComponent(RunsPage);
    fixture.autoDetectChanges();
    TestBed.tick();
    http.expectOne('/api/v1/runs?limit=50').flush(runs);
    await fixture.whenStable();
    return fixture;
  }

  it('lists runs from the API', async () => {
    const fixture = await render([RUN]);

    const row = (fixture.nativeElement as HTMLElement).querySelector('.run-row');
    expect(row?.textContent).toContain('hsanjebri/demo-app');
    expect(row?.textContent).toContain('PR opened');
  });

  it('does not submit an invalid repository and shows why', async () => {
    const fixture = await render([]);
    const el = fixture.nativeElement as HTMLElement;

    const input = el.querySelector<HTMLInputElement>('input[placeholder="owner/name"]')!;
    input.value = 'not a repo';
    input.dispatchEvent(new Event('input'));
    el.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
    await fixture.whenStable();

    http.expectNone((req) => req.method === 'POST');
    expect(el.querySelector('[role="alert"]')?.textContent).toContain('owner/name');
  });

  it('starts a run with an idempotency key and opens its page', async () => {
    const fixture = await render([]);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const el = fixture.nativeElement as HTMLElement;

    const input = el.querySelector<HTMLInputElement>('input[placeholder="owner/name"]')!;
    input.value = 'hsanjebri/demo-app';
    input.dispatchEvent(new Event('input'));
    el.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
    await fixture.whenStable();

    const post = http.expectOne((req) => req.method === 'POST' && req.url === '/api/v1/runs');
    expect(post.request.body).toEqual({ repository: 'hsanjebri/demo-app' });
    expect(post.request.headers.get('Idempotency-Key')).toMatch(/^[0-9a-f-]{36}$/);
    post.flush({ ...RUN, status: 'QUEUED' });
    await fixture.whenStable();

    expect(navigate).toHaveBeenCalledWith(['/runs', RUN.id]);
  });
});
