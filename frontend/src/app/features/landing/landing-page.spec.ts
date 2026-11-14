import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { LandingPage } from './landing-page';

describe('LandingPage', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [LandingPage],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
  });

  async function render() {
    const fixture = TestBed.createComponent(LandingPage);
    fixture.autoDetectChanges();
    TestBed.tick();
    TestBed.inject(HttpTestingController).expectOne('/actuator/health/readiness').flush({ status: 'UP' });
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('leads with the product promise and a way into the dashboard', async () => {
    const el = await render();

    expect(el.querySelector('h1')?.textContent).toContain('Spring Boot 3 → 4');
    expect(el.querySelector('a[href="/new"]')?.textContent).toContain('Start a migration');
    expect(el.querySelector('a[href="/overview"]')).not.toBeNull();
  });

  it('labels the terminal as an illustrative demo, not live data', async () => {
    const el = await render();

    expect(el.querySelector('.window-tag')?.textContent).toContain('Illustrative demo');
  });

  it('states that the recipes are not bundled', async () => {
    const el = await render();

    expect(el.querySelector('footer')?.textContent).toContain('OpenRewrite recipes are not included');
  });

  it('shows whether the local API is online', async () => {
    const el = await render();

    expect(el.querySelector('.status')?.textContent).toContain('online');
  });
});
