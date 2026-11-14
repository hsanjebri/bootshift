import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { BackendHealth } from '../../core/health';
import { PaletteControl } from '../../core/palette-control';
import { Icon, IconName } from '../../shared/icon';
import { Starfield } from '../../shared/starfield';

interface DemoLine {
  mark: '●' | '✗' | '✓' | '$';
  tone: 'cmd' | 'ok' | 'bad' | 'done';
  stage: string;
  text: string;
}

interface Feature {
  icon: IconName;
  title: string;
  body: string;
}

/** Illustrative replay of a run. Not live data; the page labels it as a demo. */
const DEMO: DemoLine[] = [
  { mark: '$', tone: 'cmd', stage: '', text: 'bootshift run acme/shop-api' },
  { mark: '●', tone: 'ok', stage: 'clone', text: 'working copy ready, credentials removed' },
  { mark: '●', tone: 'ok', stage: 'baseline', text: '212 tests · 2 already failing, recorded' },
  { mark: '●', tone: 'ok', stage: 'scan', text: 'Spring Boot 3.5.16 · Java 21 · 3 modules' },
  { mark: '●', tone: 'ok', stage: 'recipes', text: 'UpgradeSpringBoot_4_0 + 4.1 step · 148 files' },
  { mark: '✗', tone: 'bad', stage: 'verify', text: '6 compile errors · AntPathRequestMatcher not found' },
  { mark: '●', tone: 'ok', stage: 'repair', text: 'iteration 1 · PathPatternRequestMatcher.pathPattern(…)' },
  { mark: '●', tone: 'ok', stage: 'verify', text: '212 tests · same 2 failing · no new failures' },
  { mark: '●', tone: 'ok', stage: 'review', text: 'guards passed · 0 tests disabled · 0 CI files touched' },
  { mark: '✓', tone: 'done', stage: 'done', text: 'pull request opened on bootshift/spring-boot-4' },
];

@Component({
  selector: 'bs-landing-page',
  imports: [RouterLink, Icon, Starfield],
  templateUrl: './landing-page.html',
  styleUrl: './landing-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LandingPage {
  protected readonly palette = inject(PaletteControl);
  protected readonly health = inject(BackendHealth);

  private readonly shown = signal(1);
  protected readonly demo = computed(() => DEMO.slice(0, this.shown()));
  protected readonly demoDone = computed(() => this.shown() >= DEMO.length);

  protected readonly steps = [
    { n: '01', title: 'Baseline', body: 'Build and test the untouched project, so old failures are never blamed on the upgrade.' },
    { n: '02', title: 'Recipes', body: 'Run pinned OpenRewrite migrations first. Deterministic changes land in their own commit.' },
    { n: '03', title: 'Repair', body: 'An agent with no shell fixes what still fails, one small patch at a time, inside hard budgets.' },
    { n: '04', title: 'Verify', body: 'Rebuild in a fresh sandbox, compare with the baseline, run the guards, then open a PR.' },
  ];

  protected readonly features: Feature[] = [
    { icon: 'terminal', title: 'No shell for the model', body: 'The agent reads, searches and patches through exact-match tools. Every edit carries a reason.' },
    { icon: 'shield', title: 'Guardrails in code', body: 'Deleting tests, disabling them or touching CI and build repositories is refused or forces a draft.' },
    { icon: 'bolt', title: 'Sandboxed builds', body: 'Each build runs in a throwaway container with no secrets and no internet beyond a dependency proxy.' },
    { icon: 'info', title: 'Honest reports', body: 'Behaviour changes a green build cannot catch go under "Review by hand". Untested code is named.' },
    { icon: 'plug', title: 'MCP built in', body: 'Start and follow runs from Claude Code or Cursor through the same services as the API.' },
    { icon: 'repo', title: 'Self-hosted', body: 'Runs on your machine against your code. The OpenRewrite recipes are never bundled or resold.' },
  ];

  protected readonly stack = ['Spring Boot 4.1', 'OpenRewrite', 'Spring AI 2.0', 'PostgreSQL 18', 'Angular 22', 'Docker'];

  constructor() {
    const reduced = typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (reduced) {
      this.shown.set(DEMO.length);
      return;
    }
    // Reveal one line at a time, pause at the end, then replay.
    let pause = 0;
    const timer = setInterval(() => {
      if (this.shown() < DEMO.length) {
        this.shown.update((n) => n + 1);
      } else if (++pause > 6) {
        pause = 0;
        this.shown.set(1);
      }
    }, 750);
    inject(DestroyRef).onDestroy(() => clearInterval(timer));
  }
}
