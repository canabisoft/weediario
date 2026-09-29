package br.com.weediario.identity.domain.model;

import br.com.weediario.shared.domain.DomainException;
import br.com.weediario.shared.domain.Guard;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Apelido público do grower. É a única identidade exibida na plataforma.
 */
public record Username(String value) {

    public static final int MIN_LENGTH = 3;
    public static final int MAX_LENGTH = 30;
    private static final Pattern ALLOWED = Pattern.compile("^[A-Za-z0-9_.]+$");
    /** Colidem com rotas (/growers/ranking, /me) ou induzem a confusão com a equipe. */
    private static final Set<String> RESERVED = Set.of(
            "ranking", "me", "admin", "administrador", "moderador", "moderator", "suporte", "support",
            "weediario", "sistema", "system", "root", "api");

    public Username {
        value = Guard.lengthBetween(Guard.notBlank(value, "Nome de usuário"), MIN_LENGTH, MAX_LENGTH, "Nome de usuário");
        if (!ALLOWED.matcher(value).matches()) {
            throw new DomainException("Nome de usuário aceita apenas letras, números, '_' e '.'");
        }
    }

    /** Verificado só no cadastro, para não impedir o carregamento de contas antigas. */
    public boolean isReserved() {
        return RESERVED.contains(value.toLowerCase(Locale.ROOT));
    }

    @Override
    public String toString() {
        return value;
    }
}
