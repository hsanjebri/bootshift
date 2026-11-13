import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    title: 'Runs · Bootshift',
    loadComponent: () => import('./features/runs/runs-page').then((m) => m.RunsPage),
  },
  {
    path: 'runs/:id',
    title: 'Run · Bootshift',
    loadComponent: () => import('./features/run-detail/run-detail-page').then((m) => m.RunDetailPage),
  },
  { path: '**', redirectTo: '' },
];
