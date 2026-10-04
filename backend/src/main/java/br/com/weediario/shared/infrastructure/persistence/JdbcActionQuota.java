package br.com.weediario.shared.infrastructure.persistence;

import br.com.weediario.shared.application.ActionQuota;
import br.com.weediario.shared.application.QuotaExceededException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Cotas numa tabela, com janela fixa e incremento atômico no próprio upsert: requisições simultâneas da mesma conta
 * não leem o mesmo valor (a linha fica travada até o fim da transação), então ninguém passa da cota em paralelo.
 * <p>
 * A chave é um HMAC de (ação, conta) com uma subchave derivada da chave do JWT: um dump do banco não diz quem fez o
 * quê, nem permite cruzar o horário de uma cota de denúncias com o horário de uma denúncia.
 */
@Component
public class JdbcActionQuota implements ActionQuota {

    private static final String ALGORITHM = "HmacSHA256";

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final SecretKeySpec key;

    public JdbcActionQuota(NamedParameterJdbcTemplate jdbc, Clock clock, SecretKey jwtSigningKey) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.key = new SecretKeySpec(hmac(new SecretKeySpec(jwtSigningKey.getEncoded(), ALGORITHM),
                "weediario/action-quota/v1"), ALGORITHM);
    }

    @Override
    public void consume(UUID accountId, Limit limit) {
        Instant now = clock.instant();
        Usage usage = jdbc.queryForObject("""
                insert into action_quotas as q (quota_key, used, expires_at)
                values (:key, 1, :expires)
                on conflict (quota_key) do update set
                    used = case when q.expires_at <= :now then 1 else q.used + 1 end,
                    expires_at = case when q.expires_at <= :now then :expires else q.expires_at end
                returning used, expires_at""",
                new MapSqlParameterSource("key", keyOf(accountId, limit))
                        .addValue("now", Timestamp.from(now))
                        .addValue("expires", Timestamp.from(now.plus(limit.window()))),
                (rs, n) -> new Usage(rs.getInt("used"), rs.getTimestamp("expires_at").toInstant()));
        if (usage != null && usage.used() > limit.max()) {
            throw new QuotaExceededException(Duration.between(now, usage.expiresAt()));
        }
    }

    /** Minimização: janelas vencidas não servem para nada. */
    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT15M")
    void purgeExpired() {
        jdbc.update("delete from action_quotas where expires_at < :now",
                new MapSqlParameterSource("now", Timestamp.from(clock.instant())));
    }

    private String keyOf(UUID accountId, Limit limit) {
        return HexFormat.of().formatHex(hmac(key, limit.action() + ":" + accountId));
    }

    private static byte[] hmac(SecretKeySpec key, String message) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC indisponível na JVM", e);
        }
    }

    private record Usage(int used, Instant expiresAt) {
    }
}
