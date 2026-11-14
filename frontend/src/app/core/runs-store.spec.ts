import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ApplicationRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { RUNS_URL, aRun } from '../testing/fixtures';
import { RunsStore } from './runs-store';
import { Toasts } from './toasts';

describe('RunsStore', () => {
  let http: HttpTestingController;
  let store: RunsStore;
  let toasts: Toasts;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    store = TestBed.inject(RunsStore);
    toasts = TestBed.inject(Toasts);
  });

  async function load(runs: ReturnType<typeof aRun>[]) {
    TestBed.tick();
    http.expectOne(RUNS_URL).flush(runs);
    await TestBed.inject(ApplicationRef).whenStable();
    TestBed.tick();
  }

  it('counts active runs and summarizes results', async () => {
    await load([aRun(), aRun({ id: 'b', status: 'BASELINE', finishedAt: null }), aRun({ id: 'c', status: 'CANCELLED' })]);

    expect(store.active().map((r) => r.id)).toEqual(['b']);
    expect(store.stats()).toEqual({ total: 3, active: 1, opened: 1, stopped: 1, successRate: 50 });
  });

  it('stays quiet on the first load', async () => {
    await load([aRun()]);

    expect(toasts.list()).toHaveLength(0);
  });

  it('announces a run that finished since the last refresh', async () => {
    await load([aRun({ status: 'REVIEW', finishedAt: null })]);
    store.refresh();
    await load([aRun({ status: 'PR_OPENED' })]);

    expect(toasts.list()).toHaveLength(1);
    expect(toasts.list()[0]).toMatchObject({ tone: 'success', title: 'hsanjebri/demo-app: PR opened' });
  });

  it('marks failed runs as danger and includes the reason', async () => {
    await load([aRun({ status: 'VERIFY', finishedAt: null })]);
    store.refresh();
    await load([aRun({ status: 'FAILED', failureReason: 'Build timed out' })]);

    expect(toasts.list()[0]).toMatchObject({ tone: 'danger', body: 'Build timed out' });
  });
});
