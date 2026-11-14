import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { Run } from '../../core/run.model';
import { RUNS_URL, aRun } from '../../testing/fixtures';
import { RunsPage } from './runs-page';

describe('RunsPage (overview)', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<RunsPage>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [RunsPage],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  /** Renders the page and answers the shared store's list request (whenStable waits for pending HTTP). */
  async function render(runs: Run[]) {
    fixture = TestBed.createComponent(RunsPage);
    fixture.autoDetectChanges();
    TestBed.tick();
    http.expectOne(RUNS_URL).flush(runs);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('lists runs with their status', async () => {
    const el = await render([aRun()]);

    const row = el.querySelector('.run-row');
    expect(row?.textContent).toContain('hsanjebri/demo-app');
    expect(row?.textContent).toContain('PR opened');
  });

  it('shows a Live now panel only for active runs', async () => {
    const el = await render([aRun(), aRun({ id: 'b', repository: 'hsanjebri/busy', status: 'RECIPE', finishedAt: null })]);

    const live = el.querySelector('.live');
    expect(live?.textContent).toContain('hsanjebri/busy');
    expect(live?.textContent).toContain('Recipes');
    expect(live?.textContent).not.toContain('hsanjebri/demo-app');
  });

  it('filters by repository name', async () => {
    const el = await render([aRun(), aRun({ id: 'c', repository: 'acme/shop' })]);

    const search = el.querySelector<HTMLInputElement>('#run-search')!;
    search.value = 'acme';
    search.dispatchEvent(new Event('input'));
    await fixture.whenStable();

    const rows = [...el.querySelectorAll('.run-row')].map((r) => r.textContent);
    expect(rows).toHaveLength(1);
    expect(rows[0]).toContain('acme/shop');
  });

  it('computes the success rate from finished runs only', async () => {
    const el = await render([
      aRun(),
      aRun({ id: 'd', status: 'FAILED' }),
      aRun({ id: 'e', status: 'VERIFY', finishedAt: null }),
    ]);

    expect(el.querySelector('.stats')?.textContent).toContain('50%');
  });

  it('invites the user to start a run when there are none', async () => {
    const el = await render([]);

    expect(el.querySelector('.empty')?.textContent).toContain('No runs yet');
    expect(el.querySelector('.empty a[href="/new"]')).not.toBeNull();
  });
});
