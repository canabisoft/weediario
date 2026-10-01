import { HttpErrorResponse } from '@angular/common/http';
import { Problem } from './api.models';

/**
 * Mensagem amigável a partir de um erro HTTP. O backend já devolve textos em português e sem detalhes
 * internos; aqui só tratamos rede fora do ar e respostas inesperadas.
 */
export function problemMessage(error: unknown, fallback = 'Algo deu errado. Tente novamente.'): string {
  if (!(error instanceof HttpErrorResponse)) {
    return fallback;
  }
  if (error.status === 0) {
    return 'Sem conexão com o servidor. Verifique sua internet.';
  }
  const problem = error.error as Problem | null;
  const violation = problem?.violations?.[0];
  if (violation) {
    return `${fieldLabel(violation.field)}: ${violation.message}`;
  }
  return problem?.detail ?? fallback;
}

function fieldLabel(field: string): string {
  const labels: Record<string, string> = {
    username: 'Apelido',
    password: 'Senha',
    newPassword: 'Senha nova',
    currentPassword: 'Senha atual',
    recoveryVerifier: 'Frase de recuperação',
    title: 'Título',
    strainName: 'Strain',
    report: 'Relato',
    body: 'Comentário',
    name: 'Nome',
    bio: 'Bio',
  };
  return labels[field] ?? field;
}
