package br.com.weediario.identity.application.port;

/**
 * Gera refresh tokens opacos de alta entropia e calcula o hash usado para persistência/lookup.
 */
public interface RefreshTokenCodec {

    String generate();

    String hash(String rawToken);
}
