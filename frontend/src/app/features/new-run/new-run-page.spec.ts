import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { Run } from '../../core/run.model';
import { RUNS_URL, aRun } from '../../testing/fixtures';
import { NewRunPage } from './new-run-page';

describe('NewRunPage', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<NewRunPage>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [NewRunPage],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function render(history: Run[] = []) {
    fixture = TestBed.createComponent(NewRunPage);
    fixture.autoDetectChanges();
    TestBed.tick();
    http.expectOne(RUNS_URL).flush(history);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  function type(el: HTMLElement, value: string) {
    const input = el.querySelector<HTMLInputElement>('input[placeholder="owner/name"]')!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  it('does not submit an invalid repository and shows why', async () => {
    const el = await render();
    type(el, 'not a repo');
    el.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
    await fixture.whenStable();

    http.expectNone((req) => req.method === 'POST');
    expect(el.querySelector('[role="alert"]')?.textContent).toContain('owner/name');
  });

  it('starts a run with an idempotency key and opens its page', async () => {
    const el = await render();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    type(el, 'hsanjebri/demo-app');
    el.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
    await fixture.whenStable();

    const post = http.expectOne((req) => req.method === 'POST' && req.url === '/api/v1/runs');
    expect(post.request.body).toEqual({ repository: 'hsanjebri/demo-app' });
    expect(post.request.headers.get('Idempotency-Key')).toMatch(/^[0-9a-f-]{36}$/);
    post.flush(aRun({ status: 'QUEUED' }));
    await fixture.whenStable();
    http.match(RUNS_URL).forEach((req) => req.flush([]));

    expect(navigate).toHaveBeenCalledWith(['/runs', aRun().id]);
  });

  it('suggests repositories from earlier runs', async () => {
    const el = await render([aRun({ repository: 'acme/shop' })]);

    const chips = [...el.querySelectorAll('.chip')].map((c) => c.textContent?.trim());
    expect(chips).toEqual(['acme/shop']);

    (el.querySelector('.chip') as HTMLButtonElement).click();
    await fixture.whenStable();
    expect(el.querySelector<HTMLInputElement>('input[placeholder="owner/name"]')!.value).toBe('acme/shop');
  });
});
