import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { Router } from '@angular/router';

import { REPOSITORY_PATTERN } from '../core/validation';
import { RunsApi } from '../core/runs-api';
import { RunsStore } from '../core/runs-store';
import { stateLabel } from '../core/run.model';
import { Toasts } from '../core/toasts';
import { Icon, IconName } from './icon';

interface Command {
  id: string;
  label: string;
  hint: string;
  icon: IconName;
  group: 'Actions' | 'Go to' | 'Recent runs';
  run: () => void | Promise<void>;
}

/** Ctrl/⌘+K: navigate, open recent runs, or start a run by typing owner/name. */
@Component({
  selector: 'bs-command-palette',
  imports: [Icon],
  templateUrl: './command-palette.html',
  styleUrl: './command-palette.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandPalette {
  private readonly router = inject(Router);
  private readonly api = inject(RunsApi);
  private readonly store = inject(RunsStore);
  private readonly toasts = inject(Toasts);

  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
  private readonly input = viewChild.required<ElementRef<HTMLInputElement>>('input');

  protected readonly query = signal('');
  protected readonly selected = signal(0);

  protected readonly commands = computed<Command[]>(() => {
    const q = this.query().trim();
    const lower = q.toLowerCase();
    const list: Command[] = [];

    if (REPOSITORY_PATTERN.test(q)) {
      list.push({
        id: 'start',
        label: `Start a run for ${q}`,
        hint: 'Enter',
        icon: 'play',
        group: 'Actions',
        run: () => this.start(q),
      });
    }

    const pages: Command[] = [
      { id: 'overview', label: 'Overview', hint: 'Runs and live status', icon: 'overview', group: 'Go to', run: () => this.go(['/']) },
      { id: 'new', label: 'New run', hint: 'N', icon: 'plus', group: 'Go to', run: () => this.go(['/new']) },
      { id: 'repos', label: 'Repositories', hint: 'From your runs', icon: 'repo', group: 'Go to', run: () => this.go(['/repositories']) },
      { id: 'settings', label: 'Settings', hint: 'Theme and API', icon: 'settings', group: 'Go to', run: () => this.go(['/settings']) },
    ];
    list.push(...pages.filter((p) => !lower || p.label.toLowerCase().includes(lower)));

    const runs = this.store.runs
      .value()
      .filter((r) => !lower || r.repository.toLowerCase().includes(lower))
      .slice(0, 6)
      .map<Command>((r) => ({
        id: `run-${r.id}`,
        label: r.repository,
        hint: stateLabel(r.status),
        icon: 'git',
        group: 'Recent runs',
        run: () => this.go(['/runs', r.id]),
      }));
    list.push(...runs);
    return list;
  });

  protected readonly groups = computed(() => {
    const order: Command['group'][] = ['Actions', 'Go to', 'Recent runs'];
    const commands = this.commands();
    return order
      .map((name) => ({ name, items: commands.filter((c) => c.group === name) }))
      .filter((g) => g.items.length > 0);
  });

  open(): void {
    this.query.set('');
    this.selected.set(0);
    const dialog = this.dialog().nativeElement;
    if (!dialog.open) dialog.showModal();
    queueMicrotask(() => this.input().nativeElement.focus());
  }

  close(): void {
    this.dialog().nativeElement.close();
  }

  protected onInput(value: string): void {
    this.query.set(value);
    this.selected.set(0);
  }

  protected onKeydown(event: KeyboardEvent): void {
    const count = this.commands().length;
    if (event.key === 'ArrowDown' && count) {
      event.preventDefault();
      this.selected.update((i) => (i + 1) % count);
    } else if (event.key === 'ArrowUp' && count) {
      event.preventDefault();
      this.selected.update((i) => (i - 1 + count) % count);
    } else if (event.key === 'Enter') {
      event.preventDefault();
      const command = this.commands()[this.selected()];
      if (command) void this.execute(command);
    }
  }

  protected indexOf(command: Command): number {
    return this.commands().indexOf(command);
  }

  protected async execute(command: Command): Promise<void> {
    this.close();
    await command.run();
  }

  /** Closes when the backdrop (the dialog element itself) is clicked. */
  protected onDialogClick(event: MouseEvent): void {
    if (event.target === this.dialog().nativeElement) this.close();
  }

  private async go(commands: unknown[]): Promise<void> {
    await this.router.navigate(commands);
  }

  private async start(repository: string): Promise<void> {
    try {
      const run = await this.api.start(repository, null);
      this.store.refresh();
      await this.router.navigate(['/runs', run.id]);
    } catch (error) {
      const detail = error instanceof HttpErrorResponse ? (error.error as { detail?: string } | null)?.detail : null;
      this.toasts.show({ tone: 'danger', title: 'Could not start the run', body: detail ?? undefined });
    }
  }
}
