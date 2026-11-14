import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    title: 'Overview · Bootshift',
    loadComponent: () => import('./features/runs/runs-page').then((m) => m.RunsPage),
  },
  {
    path: 'new',
    title: 'New run · Bootshift',
    loadComponent: () => import('./features/new-run/new-run-page').then((m) => m.NewRunPage),
  },
  {
    path: 'runs/:id',
    title: 'Run · Bootshift',
    loadComponent: () => import('./features/run-detail/run-detail-page').then((m) => m.RunDetailPage),
  },
  {
    path: 'repositories',
    title: 'Repositories · Bootshift',
    loadComponent: () => import('./features/repositories/repositories-page').then((m) => m.RepositoriesPage),
  },
  {
    path: 'settings',
    title: 'Settings · Bootshift',
    loadComponent: () => import('./features/settings/settings-page').then((m) => m.SettingsPage),
  },
  { path: '**', redirectTo: '' },
];
