package br.com.weediario.identity.application.port;

import br.com.weediario.identity.domain.model.User;

import java.time.Instant;

public interface AccessTokenIssuer {

    IssuedAccessToken issue(User user);

    record IssuedAccessToken(String value, Instant expiresAt) {
    }
}
