import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    title: 'weediário',
    loadComponent: () => import('./home/home.page').then((m) => m.HomePage),
  },
  {
    path: 'ferramentas',
    title: 'Ferramentas · weediário',
    loadComponent: () => import('./tools/tools.page').then((m) => m.ToolsPage),
  },
  {
    path: 'ferramentas/vpd',
    title: 'Calculadora de VPD · weediário',
    loadComponent: () => import('./tools/vpd/vpd-calculator.page').then((m) => m.VpdCalculatorPage),
  },
  {
    path: 'ferramentas/ec-ppm',
    title: 'Conversor EC / PPM · weediário',
    loadComponent: () => import('./tools/ec/ec-converter.page').then((m) => m.EcConverterPage),
  },
  {
    path: 'ajustes',
    title: 'Ajustes · weediário',
    loadComponent: () => import('./settings/settings.page').then((m) => m.SettingsPage),
  },
  { path: '**', redirectTo: '' },
];
