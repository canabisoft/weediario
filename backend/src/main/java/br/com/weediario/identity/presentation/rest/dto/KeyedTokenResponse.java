package br.com.weediario.identity.presentation.rest.dto;

import java.time.Instant;

/**
 * Sessão aberta com uma chave de acesso nova (cadastro, recuperação, migração de conta antiga). É a única vez que a
 * chave sai do servidor; a resposta vai com {@code Cache-Control: no-store}.
 */
public record KeyedTokenResponse(String accessToken, String tokenType, Instant expiresAt, String accessKey) {

    @Override
    public String toString() {
        return "KeyedTokenResponse[***]";
    }
}
