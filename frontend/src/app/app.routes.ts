import { Routes } from '@angular/router';
import { authGuard, guestGuard, moderatorGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  {
    path: '',
    title: 'weediário',
    loadComponent: () => import('./home/home.page').then((m) => m.HomePage),
  },
  {
    path: 'entrar',
    title: 'Entrar · weediário',
    canActivate: [guestGuard],
    loadComponent: () => import('./auth/login.page').then((m) => m.LoginPage),
  },
  {
    path: 'cadastro',
    title: 'Criar conta · weediário',
    canActivate: [guestGuard],
    loadComponent: () => import('./auth/register.page').then((m) => m.RegisterPage),
  },
  {
    path: 'recuperar',
    title: 'Recuperar conta · weediário',
    canActivate: [guestGuard],
    loadComponent: () => import('./auth/recover.page').then((m) => m.RecoverPage),
  },
  {
    path: 'frase-de-recuperacao',
    title: 'Frase de recuperação · weediário',
    canActivate: [authGuard],
    loadComponent: () => import('./auth/recovery-phrase.page').then((m) => m.RecoveryPhrasePage),
  },
  {
    path: 'diarios',
    title: 'Diários · weediário',
    loadComponent: () => import('./diaries/diaries.page').then((m) => m.DiariesPage),
  },
  {
    path: 'diarios/novo',
    title: 'Iniciar diário · weediário',
    canActivate: [authGuard],
    loadComponent: () => import('./diaries/new-diary.page').then((m) => m.NewDiaryPage),
  },
  {
    path: 'diarios/:id',
    title: 'Diário · weediário',
    loadComponent: () => import('./diaries/diary-detail.page').then((m) => m.DiaryDetailPage),
  },
  {
    path: 'diarios/:id/update',
    title: 'Novo update · weediário',
    canActivate: [authGuard],
    loadComponent: () => import('./diaries/new-update.page').then((m) => m.NewUpdatePage),
  },
  {
    path: 'mais-usados',
    title: 'Mais usados · weediário',
    loadComponent: () => import('./catalog/most-used.page').then((m) => m.MostUsedPage),
  },
  {
    path: 'receitas',
    title: 'Receitas de nutrição · weediário',
    loadComponent: () => import('./recipes/recipes.page').then((m) => m.RecipesPage),
  },
  {
    path: 'receitas/nova',
    title: 'Nova receita · weediário',
    canActivate: [authGuard],
    loadComponent: () => import('./recipes/recipe-editor.page').then((m) => m.RecipeEditorPage),
  },
  {
    path: 'receitas/:id',
    title: 'Receita · weediário',
    loadComponent: () => import('./recipes/recipe-detail.page').then((m) => m.RecipeDetailPage),
  },
  {
    path: 'receitas/:id/editar',
    title: 'Editar receita · weediário',
    canActivate: [authGuard],
    loadComponent: () => import('./recipes/recipe-editor.page').then((m) => m.RecipeEditorPage),
  },
  {
    path: 'strains',
    title: 'Catálogo · weediário',
    loadComponent: () => import('./catalog/catalog.page').then((m) => m.CatalogPage),
  },
  {
    path: 'strains/:id',
    title: 'Strain · weediário',
    data: { kind: 'strain' },
    loadComponent: () => import('./catalog/catalog-detail.page').then((m) => m.CatalogDetailPage),
  },
  {
    path: 'produtos/:id',
    title: 'Produto · weediário',
    loadComponent: () => import('./catalog/product-detail.page').then((m) => m.ProductDetailPage),
  },
  {
    path: 'breeders/programa',
    title: 'Programa de breeders · weediário',
    canActivate: [authGuard],
    loadComponent: () => import('./catalog/breeder-program.page').then((m) => m.BreederProgramPage),
  },
  {
    path: 'dados-abertos',
    title: 'Dados abertos · weediário',
    loadComponent: () => import('./open-data/open-data.page').then((m) => m.OpenDataPage),
  },
  {
    path: 'bancos/:id',
    title: 'Banco de sementes · weediário',
    data: { kind: 'seedbank' },
    loadComponent: () => import('./catalog/catalog-detail.page').then((m) => m.CatalogDetailPage),
  },
  {
    path: 'growers',
    title: 'Ranking de growers · weediário',
    loadComponent: () => import('./growers/growers-ranking.page').then((m) => m.GrowersRankingPage),
  },
  {
    path: 'growers/:username',
    title: 'Grower · weediário',
    loadComponent: () => import('./growers/grower-profile.page').then((m) => m.GrowerProfilePage),
  },
  {
    path: 'equipamentos',
    title: 'Equipamentos · weediário',
    canActivate: [authGuard],
    loadComponent: () => import('./setups/setups.page').then((m) => m.SetupsPage),
  },
  {
    path: 'perfil',
    title: 'Meu perfil · weediário',
    canActivate: [authGuard],
    loadComponent: () => import('./profile/my-profile.page').then((m) => m.MyProfilePage),
  },
  {
    path: 'tratamento',
    title: 'Tratamento · weediário',
    canActivate: [authGuard],
    loadComponent: () => import('./treatment/treatment.page').then((m) => m.TreatmentPage),
  },
  {
    path: 'tratamento/:patientId',
    title: 'Tratamento · weediário',
    canActivate: [authGuard],
    loadComponent: () => import('./treatment/patient.page').then((m) => m.TreatmentPatientPage),
  },
  {
    path: 'notificacoes',
    title: 'Notificações · weediário',
    canActivate: [authGuard],
    loadComponent: () => import('./notifications/notifications.page').then((m) => m.NotificationsPage),
  },
  {
    path: 'moderacao',
    title: 'Moderação · weediário',
    canActivate: [moderatorGuard],
    loadComponent: () => import('./moderation/moderation.page').then((m) => m.ModerationPage),
  },
  {
    path: 'termos',
    title: 'Termos de Uso · weediário',
    loadComponent: () => import('./legal/terms.page').then((m) => m.TermsPage),
  },
  {
    path: 'manifesto',
    title: 'Manifesto · weediário',
    loadComponent: () => import('./manifesto/manifesto.page').then((m) => m.ManifestoPage),
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
