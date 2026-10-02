package br.com.weediario.identity.domain.service;

import br.com.weediario.identity.domain.model.Username;
import br.com.weediario.shared.domain.DomainException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Regras da senha escolhida pela pessoa. Comprimento antes de complexidade (NIST SP 800-63B): sem exigir símbolo ou
 * maiúscula, mas recusando o que é fácil de adivinhar — senhas da lista de vazamentos mais comuns, o próprio apelido e
 * sequências de um caractere só. A lista vem embutida no app: nada é consultado fora (nem um prefixo de hash).
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 10;
    /** Teto para o Argon2 não virar vetor de sobrecarga com senhas gigantes. */
    public static final int MAX_LENGTH = 128;
    private static final String COMMON_PASSWORDS = "/identity/common-passwords.txt";

    private static final Set<String> COMMON = load();

    private PasswordPolicy() {
    }

    /** @return a senha como veio (sem normalizar: espaços e maiúsculas fazem parte dela) */
    public static String requireAcceptable(String password, Username username) {
        if (password == null || password.isBlank()) {
            throw new DomainException("Informe uma senha");
        }
        int length = password.codePointCount(0, password.length());
        if (length < MIN_LENGTH) {
            throw new DomainException("A senha precisa de pelo menos " + MIN_LENGTH + " caracteres");
        }
        if (length > MAX_LENGTH) {
            throw new DomainException("A senha pode ter no máximo " + MAX_LENGTH + " caracteres");
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (COMMON.contains(lower)) {
            throw new DomainException("Essa senha está entre as mais usadas (e vazadas). Escolha outra");
        }
        if (username != null && lower.contains(username.value().toLowerCase(Locale.ROOT))) {
            throw new DomainException("A senha não pode conter o seu apelido");
        }
        if (lower.codePoints().distinct().count() < 4) {
            throw new DomainException("A senha repete poucos caracteres. Que tal uma frase curta?");
        }
        return password;
    }

    private static Set<String> load() {
        try (InputStream in = PasswordPolicy.class.getResourceAsStream(COMMON_PASSWORDS)) {
            if (in == null) {
                throw new IllegalStateException("Lista de senhas comuns ausente: " + COMMON_PASSWORDS);
            }
            Set<String> words = new HashSet<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                reader.lines()
                        .map(String::strip)
                        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                        .forEach(line -> words.add(line.toLowerCase(Locale.ROOT)));
            }
            return Set.copyOf(words);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
