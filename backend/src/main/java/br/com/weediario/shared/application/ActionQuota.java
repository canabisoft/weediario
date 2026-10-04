package br.com.weediario.shared.application;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/**
 * Cota de uso por conta e por ação (denunciar, exportar os dados, seguir...), nunca por IP. Cada chamada consome uma
 * unidade; acabou a cota da janela, lança {@link QuotaExceededException}.
 * <p>
 * Chame dentro da transação do caso de uso e depois das validações: se a ação falhar, o consumo é desfeito junto e
 * tentativa recusada não gasta cota.
 */
public interface ActionQuota {

    void consume(UUID accountId, Limit limit);

    /**
     * @param action nome estável da ação (entra na chave guardada; mudar o nome zera as contagens)
     * @param max    quantas vezes cabem na janela
     */
    record Limit(String action, int max, Duration window) {

        public Limit {
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(window, "window");
            if (action.isBlank() || max < 1 || window.isNegative() || window.isZero()) {
                throw new IllegalArgumentException("Cota inválida: " + action);
            }
        }
    }
}
