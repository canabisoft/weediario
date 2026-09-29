import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/** Páginas que exigem login mandam para /entrar e voltam para onde o usuário estava. */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  return auth.isLoggedIn() ? true : inject(Router).createUrlTree(['/entrar'], { queryParams: { voltar: state.url } });
};

/** Evita mostrar login/cadastro para quem já entrou. */
export const guestGuard: CanActivateFn = () => {
  return inject(AuthService).isLoggedIn() ? inject(Router).createUrlTree(['/perfil']) : true;
};
