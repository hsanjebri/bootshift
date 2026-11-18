import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { GitHub, ManifestForm } from '../core/github';
import { STATUS_URL, gitHubStatus } from '../testing/fixtures';
import { GitHubConnect } from './github-connect';

describe('GitHubConnect', () => {
  let http: HttpTestingController;
  let submitted: ManifestForm[];

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [GitHubConnect],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    submitted = [];
    // Never leave the test page: record the form instead of posting it to GitHub.
    const github = TestBed.inject(GitHub);
    const connect = github.connect.bind(github);
    vi.spyOn(github, 'connect').mockImplementation((org) => connect(org, (form) => submitted.push(form)));
  });

  async function render() {
    const fixture = TestBed.createComponent(GitHubConnect);
    fixture.autoDetectChanges();
    TestBed.tick();
    // The GitHub service loads its status; whenStable waits for pending HTTP.
    http.match(STATUS_URL).forEach((r) => r.flush(gitHubStatus(false)));
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  function button(el: HTMLElement, text: string): HTMLButtonElement {
    return [...el.querySelectorAll('button')].find((b) => b.textContent?.includes(text))!;
  }

  it('asks the backend for a manifest and posts it to GitHub', async () => {
    const { fixture, el } = await render();

    button(el, 'Connect GitHub').click();
    const req = http.expectOne('/api/v1/github/manifest');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ dashboardUrl: location.origin, organization: null });
    TestBed.tick();
    expect(button(el, 'Opening GitHub').disabled).toBe(true);
    req.flush({ action: 'https://github.com/settings/apps/new?state=s', manifest: '{"name":"bootshift-abc"}' });
    await fixture.whenStable();

    expect(submitted).toEqual([{ action: 'https://github.com/settings/apps/new?state=s', manifest: '{"name":"bootshift-abc"}' }]);
  });

  it('can create the app under an organization', async () => {
    const { fixture, el } = await render();

    button(el, 'For an organization').click();
    await fixture.whenStable();
    const input = el.querySelector<HTMLInputElement>('input')!;
    input.value = 'my-org';
    input.dispatchEvent(new Event('input'));
    button(el, 'Connect GitHub').click();

    expect(http.expectOne('/api/v1/github/manifest').request.body.organization).toBe('my-org');
  });

  it('does not send an invalid organization name', async () => {
    const { fixture, el } = await render();

    button(el, 'For an organization').click();
    await fixture.whenStable();
    const input = el.querySelector<HTMLInputElement>('input')!;
    input.value = 'bad/org';
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    button(el, 'Connect GitHub').click();

    http.expectNone('/api/v1/github/manifest');
    expect(el.textContent).toContain('Not a GitHub organization name');
  });

  it("shows the backend's reason when it refuses", async () => {
    const { fixture, el } = await render();

    button(el, 'Connect GitHub').click();
    http.expectOne('/api/v1/github/manifest').flush(
      { detail: 'Only a localhost dashboard can connect GitHub' },
      { status: 400, statusText: 'Bad Request' },
    );
    await fixture.whenStable();

    expect(el.querySelector('[role="alert"]')?.textContent).toContain('Only a localhost dashboard');
    expect(button(el, 'Connect GitHub').disabled).toBe(false);
    expect(submitted).toEqual([]);
  });
});
