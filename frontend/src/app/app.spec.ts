import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { App } from './app';
import { RUNS_URL, aRun } from './testing/fixtures';

describe('App shell', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  async function render(runs = [aRun()]) {
    const fixture = TestBed.createComponent(App);
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

  it('opens the command palette with Ctrl+K', async () => {
    const el = await render();
    const dialog = el.querySelector('dialog') as HTMLDialogElement;
    dialog.showModal ??= function (this: HTMLDialogElement) { this.open = true; };

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'k', ctrlKey: true }));

    expect(dialog.open).toBe(true);
  });
});
