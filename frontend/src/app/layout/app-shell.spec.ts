import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { PaletteControl } from '../core/palette-control';
import { RUNS_URL, aRun } from '../testing/fixtures';
import { AppShell } from './app-shell';

describe('AppShell', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppShell],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  async function render(runs = [aRun()]) {
    const fixture = TestBed.createComponent(AppShell);
    fixture.autoDetectChanges();
    TestBed.tick();
    http.expectOne(RUNS_URL).flush(runs);
    http.expectOne('/actuator/health/readiness').flush({ status: 'UP' });
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows the navigation and a healthy backend', async () => {
    const el = await render();

    const links = [...el.querySelectorAll('nav a')].map((a) => a.textContent?.trim());
    expect(links).toEqual(['Overview', 'New run', 'Repositories', 'Settings']);
    expect(el.querySelector('.health')?.textContent).toContain('online');
  });

  it('shows how many runs are live next to Overview', async () => {
    const el = await render([aRun(), aRun({ id: 'x', status: 'SCANNING', finishedAt: null })]);

    expect(el.querySelector('.live-count')?.textContent?.trim()).toBe('1');
  });

  it('asks the palette to open from the search button', async () => {
    const el = await render();
    const control = TestBed.inject(PaletteControl);
    const before = control.openRequests();

    (el.querySelector('.search-trigger') as HTMLButtonElement).click();

    expect(control.openRequests()).toBe(before + 1);
  });
});
