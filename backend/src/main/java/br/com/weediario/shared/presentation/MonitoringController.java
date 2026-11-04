package br.com.weediario.shared.presentation;

import br.com.weediario.shared.infrastructure.observability.SecuritySignals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Leitura dos {@link SecuritySignals} por um monitor de fora (o agente que avisa no Telegram), para o alerta não
 * depender de alguém abrir o log. Só nomes fixos de sinal e números: nada de conta, IP ou texto escrito por usuário,
 * então nem o monitor nem o mensageiro recebem dado pessoal, e não há texto que possa dar ordem a um agente.
 * <p>
 * Fora do JWT de propósito (o monitor não é uma conta): exige o token de {@code WEEDIARIO_MONITOR_TOKEN} no cabeçalho
 * {@value #HEADER}. Sem token configurado, ou com o errado, responde 404, como se a rota não existisse.
 */
@RestController
public class MonitoringController {

    public static final String PATH = "/api/v1/ops/signals";
    static final String HEADER = "X-Weediario-Monitor";
    private static final int MIN_TOKEN_LENGTH = 32;
    private static final Logger log = LoggerFactory.getLogger(MonitoringController.class);

    private final SecuritySignals signals;
    private final byte[] expected;

    public MonitoringController(SecuritySignals signals,
                                @Value("${weediario.ops.monitor-token:}") String token) {
        this.signals = signals;
        String trimmed = token == null ? "" : token.strip();
        if (!trimmed.isEmpty() && trimmed.length() < MIN_TOKEN_LENGTH) {
            log.warn("WEEDIARIO_MONITOR_TOKEN curto demais (mínimo {} caracteres): monitor externo desligado",
                    MIN_TOKEN_LENGTH);
        }
        this.expected = trimmed.length() >= MIN_TOKEN_LENGTH ? sha256(trimmed) : null;
    }

    @GetMapping(PATH)
    ResponseEntity<SecuritySignals.Report> signals(@RequestHeader(name = HEADER, required = false) String token) {
        // Compara os hashes: tamanho fixo e tempo constante, sem vazar o comprimento do token.
        if (expected == null || token == null || !MessageDigest.isEqual(expected, sha256(token.strip()))) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(signals.report());
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM", e);
        }
    }
}
