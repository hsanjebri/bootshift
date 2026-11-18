import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormField, form, pattern } from '@angular/forms/signals';

import { GitHub } from '../core/github';
import { Icon } from './icon';

/** GitHub account and organization names. */
const LOGIN_PATTERN = /^[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})$/;

/**
 * The Connect GitHub button. One click opens GitHub with a ready-made app description; the user
 * confirms it there and comes back to install the app on the repositories they choose.
 */
@Component({
  selector: 'bs-github-connect',
  imports: [FormField, Icon],
  template: `
    <div class="actions">
      <button type="button" class="btn primary" (click)="connect()" [disabled]="busy()">
        <bs-icon name="git" [size]="15" /> {{ busy() ? 'Opening GitHub…' : 'Connect GitHub' }}
      </button>
      @if (!useOrganization()) {
        <button type="button" class="btn ghost sm" (click)="useOrganization.set(true)">For an organization</button>
      }
    </div>
    @if (useOrganization()) {
      <label class="field">
        <span class="label">Organization</span>
        <input type="text" class="mono" placeholder="my-org" autocomplete="off" spellcheck="false"
               [formField]="org.organization" />
        @if (org.organization().invalid()) {
          <span class="error" role="alert">Not a GitHub organization name</span>
        } @else {
          <span class="help">You need to be an owner of it. Leave empty for your own account.</span>
        }
      </label>
    }
    @if (error(); as message) {
      <p class="error" role="alert">{{ message }}</p>
    }
  `,
  styles: `
    :host { display: grid; gap: 12px; }
    .actions { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
    .field { display: grid; gap: 6px; max-width: 320px; }
    .label { font-size: 13px; font-weight: 600; }
    input { font: inherit; padding: 8px 10px; border-radius: 8px; border: 1px solid var(--border); background: var(--surface); color: var(--text); }
    .help { font-size: 12px; color: var(--text-muted); }
    .error { font-size: 13px; color: var(--danger); margin: 0; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GitHubConnect {
  private readonly github = inject(GitHub);

  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly useOrganization = signal(false);
  protected readonly model = signal({ organization: '' });
  protected readonly org = form(this.model, (path) => {
    pattern(path.organization, LOGIN_PATTERN);
  });

  protected async connect(): Promise<void> {
    const organization = this.useOrganization() ? this.model().organization.trim() : '';
    if (organization && !LOGIN_PATTERN.test(organization)) return;
    this.busy.set(true);
    this.error.set(null);
    try {
      // On success the browser leaves for GitHub, so busy stays on.
      await this.github.connect(organization || null);
    } catch (error) {
      const detail = error instanceof HttpErrorResponse ? (error.error as { detail?: string } | null)?.detail : null;
      this.error.set(detail ?? 'Could not reach the backend. Is it running?');
      this.busy.set(false);
    }
  }
}
