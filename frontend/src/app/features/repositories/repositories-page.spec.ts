import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { GitHubRepo } from '../../core/github';
import { REPOS_URL, RUNS_URL, STATUS_URL, aGitHubRepo, gitHubStatus } from '../../testing/fixtures';
import { RepositoriesPage } from './repositories-page';

describe('RepositoriesPage', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<RepositoriesPage>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [RepositoriesPage],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  async function render(connected: boolean, repos: GitHubRepo[] = []) {
    fixture = TestBed.createComponent(RepositoriesPage);
    fixture.autoDetectChanges();
    TestBed.tick();
    http.expectOne(RUNS_URL).flush([]);
    http.expectOne(STATUS_URL).flush(gitHubStatus(connected));
    // Answer the repositories request (sent once connected) before waiting: whenStable waits for pending HTTP.
    if (connected) {
      await Promise.resolve();
      TestBed.tick();
      http.expectOne(REPOS_URL).flush(repos);
    }
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('asks to connect GitHub when no app is configured', async () => {
    const el = await render(false);

    expect(el.querySelector('.connect')?.textContent).toContain('Connect GitHub');
    expect(el.querySelector('.gh-list')).toBeNull();
  });

  it('lists the repositories the app can see with their allowlist state', async () => {
    const el = await render(true, [aGitHubRepo({ name: 'a', allowed: true }), aGitHubRepo({ name: 'b' })]);

    const switches = [...el.querySelectorAll<HTMLButtonElement>('button[role="switch"]')];
    expect(switches.map((s) => s.getAttribute('aria-checked'))).toEqual(['true', 'false']);
    expect(el.querySelector('.count')?.textContent).toContain('1 of 2 allowed');
  });

  it('allows a repository with a PUT and updates the switch', async () => {
    const el = await render(true, [aGitHubRepo({ name: 'shop' })]);

    el.querySelector<HTMLButtonElement>('button[role="switch"]')!.click();
    const put = http.expectOne((req) => req.method === 'PUT' && req.url === '/api/v1/repositories/hsanjebri/shop');
    expect(put.request.body).toEqual({ allowed: true });
    put.flush(aGitHubRepo({ name: 'shop', allowed: true }));
    await fixture.whenStable();

    expect(el.querySelector('button[role="switch"]')?.getAttribute('aria-checked')).toBe('true');
  });

  it('does not offer to allow archived repositories', async () => {
    const el = await render(true, [aGitHubRepo({ name: 'old', archived: true })]);

    expect(el.querySelector<HTMLButtonElement>('button[role="switch"]')!.disabled).toBe(true);
  });
});
