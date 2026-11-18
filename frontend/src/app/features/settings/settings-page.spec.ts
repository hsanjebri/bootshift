import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { GitHubStatus } from '../../core/github';
import { REPOS_URL, STATUS_URL, gitHubStatus } from '../../testing/fixtures';
import { SettingsPage } from './settings-page';

describe('SettingsPage', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<SettingsPage>;

  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] });
    TestBed.configureTestingModule({
      imports: [SettingsPage],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => vi.useRealTimers());

  async function render(status: GitHubStatus, github?: string) {
    fixture = TestBed.createComponent(SettingsPage);
    if (github) fixture.componentRef.setInput('github', github);
    fixture.autoDetectChanges();
    TestBed.tick();
    http.match('/actuator/health/readiness').forEach((r) => r.flush({ status: 'UP' }));
    http.expectOne(STATUS_URL).flush(status);
    await Promise.resolve();
    TestBed.tick();
    http.match(REPOS_URL).forEach((r) => r.flush([]));
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  function button(el: HTMLElement, text: string): HTMLButtonElement | undefined {
    return [...el.querySelectorAll('button')].find((b) => b.textContent?.includes(text));
  }

  it('offers Connect GitHub when nothing is connected', async () => {
    const el = await render(gitHubStatus(false));

    expect(el.querySelector('bs-github-connect')).not.toBeNull();
    expect(el.textContent).toContain('Not connected');
    expect(el.querySelector('[role="alert"]')).toBeNull();
  });

  it("says so when GitHub's redirect could not be completed", async () => {
    const el = await render(gitHubStatus(false), 'failed');

    expect(el.querySelector('[role="alert"]')?.textContent).toContain('did not finish creating the app');
  });

  it('disconnects an app made with Connect GitHub after asking', async () => {
    const el = await render(gitHubStatus(true));
    expect(el.textContent).toContain('bootshift-test');

    button(el, 'Disconnect…')!.click();
    await fixture.whenStable();
    expect(el.textContent).toContain('forgets the app');
    button(el, 'Disconnect')!.click();

    const del = http.expectOne('/api/v1/github/app');
    expect(del.request.method).toBe('DELETE');
    del.flush(null, { status: 204, statusText: 'No Content' });
    await Promise.resolve();
    TestBed.tick();
    http.expectOne(STATUS_URL).flush(gitHubStatus(false));
    http.match(REPOS_URL).forEach((r) => r.flush([]));
    await fixture.whenStable();

    expect(el.textContent).toContain('Not connected');
  });

  it('cannot disconnect an app set on the server', async () => {
    const el = await render({ ...gitHubStatus(true), source: 'ENVIRONMENT' });

    expect(button(el, 'Disconnect')).toBeUndefined();
    expect(el.textContent).toContain('GITHUB_APP_ID');
  });
});
