package br.com.weediario.moderation.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Histórico de quem denuncia, para decidir se a denúncia conta para tirar um conteúdo do ar sozinha.
 * <p>
 * O cadastro é livre (sem e-mail obrigatório), então criar três contas e ocultar o diário de alguém seria fácil.
 * Só conta quem tem a conta há pelo menos {@link #MIN_ACCOUNT_AGE} e já mostrou uso real: publicou um update de
 * cultivo ou confirmou um e-mail. Denúncia de quem ainda não tem esse histórico é registrada e vai para a fila da
 * moderação do mesmo jeito; só não oculta nada.
 *
 * @param accountCreatedAt  quando a conta foi criada
 * @param emailVerified     tem e-mail confirmado
 * @param publishedUpdate   já publicou ao menos um update de diário
 */
public record ReporterStanding(Instant accountCreatedAt, boolean emailVerified, boolean publishedUpdate) {

    public static final Duration MIN_ACCOUNT_AGE = Duration.ofDays(7);

    public ReporterStanding {
        Objects.requireNonNull(accountCreatedAt, "accountCreatedAt");
    }

    public boolean countsTowardHiding(Instant now) {
        boolean oldEnough = !accountCreatedAt.plus(MIN_ACCOUNT_AGE).isAfter(now);
        return oldEnough && (emailVerified || publishedUpdate);
    }
}
