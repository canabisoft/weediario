import { Routes } from '@angular/router';

const comingSoon = () => import('./shared/coming-soon.page').then((m) => m.ComingSoonPage);

export const routes: Routes = [
  {
    path: '',
    title: 'weediário',
    loadComponent: () => import('./home/home.page').then((m) => m.HomePage),
  },
  {
    path: 'growers',
    title: 'Ranking de growers · weediário',
    loadComponent: () => import('./growers/growers-ranking.page').then((m) => m.GrowersRankingPage),
  },
  {
    path: 'diarios/novo',
    title: 'Iniciar diário · weediário',
    loadComponent: comingSoon,
    data: {
      heading: 'Iniciar diário',
      description: 'Em breve você cria o diário aqui, escolhendo um dos seus setups salvos com um toque.',
    },
  },
  {
    path: 'diarios',
    title: 'Diários · weediário',
    loadComponent: comingSoon,
    data: { heading: 'Diários', description: 'O feed de diários da comunidade, com filtros por fase e meio de cultivo.' },
  },
  {
    path: 'strains',
    title: 'Strains · weediário',
    loadComponent: comingSoon,
    data: { heading: 'Strains', description: 'Catálogo de genéticas com as notas dadas pela comunidade.' },
  },
  {
    path: 'equipamentos',
    title: 'Equipamentos · weediário',
    loadComponent: comingSoon,
    data: {
      heading: 'Equipamentos',
      description: 'Seus setups salvos (tenda, luz, substrato, ventilação) para reutilizar a cada novo ciclo.',
    },
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
