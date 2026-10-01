package br.com.weediario.identity.application;

import br.com.weediario.identity.domain.model.AccessKey;

/**
 * Sessão aberta junto com uma chave de acesso nova (cadastro, recuperação ou migração de conta antiga). É a única vez
 * que a chave aparece: o servidor não guarda a chave, só o hash dela.
 */
public record KeyedSession(AuthenticatedSession session, AccessKey accessKey) {
}
