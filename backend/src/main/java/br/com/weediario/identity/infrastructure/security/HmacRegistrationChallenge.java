package br.com.weediario.identity.infrastructure.security;

import br.com.weediario.identity.application.port.RegistrationChallenge;
import br.com.weediario.shared.domain.DomainException;
import br.com.weediario.shared.infrastructure.observability.SecuritySignals;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Desafios assinados com uma subchave derivada da chave do JWT (derivação por rótulo). Nada é guardado na emissão;
 * só a solução aceita fica registrada, até vencer, para não ser usada duas vezes.
 */
@Component
public class HmacRegistrationChallenge implements RegistrationChallenge {

    static final String ALGORITHM = "SHA-256";
    private static final String HMAC = "HmacSHA256";
    private static final Pattern SALT = Pattern.compile("^[0-9a-f]{24}\\?expires=(\\d{1,12})$");
    private static final Pattern HEX_64 = Pattern.compile("^[0-9a-f]{64}$");
    private static final String INVALID = "A verificação anti-robô não confere. Recarregue a página e tente de novo.";

    private final SecretKeySpec key;
    private final RegistrationChallengeProperties properties;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final SecuritySignals signals;
    private final SecureRandom random = new SecureRandom();

    public HmacRegistrationChallenge(SecretKey jwtSigningKey, RegistrationChallengeProperties properties,
                                     NamedParameterJdbcTemplate jdbc, Clock clock, SecuritySignals signals) {
        this.key = new SecretKeySpec(hmac(new SecretKeySpec(jwtSigningKey.getEncoded(), HMAC),
                "weediario/register-challenge/v1"), HMAC);
        this.properties = properties;
        this.jdbc = jdbc;
        this.clock = clock;
        this.signals = signals;
    }

    @Override
    public Challenge issue() {
        byte[] nonce = new byte[12];
        random.nextBytes(nonce);
        long expires = clock.instant().plus(properties.ttl()).getEpochSecond();
        String salt = HexFormat.of().formatHex(nonce) + "?expires=" + expires;
        long number = random.nextLong(properties.maxNumber() + 1);
        String challenge = sha256(salt + number);
        return new Challenge(ALGORITHM, challenge, salt, properties.maxNumber(), sign(challenge));
    }

    @Override
    public void redeem(Solution solution) {
        if (solution == null || solution.salt() == null || solution.challenge() == null || solution.signature() == null
                || !HEX_64.matcher(solution.challenge()).matches() || !HEX_64.matcher(solution.signature()).matches()) {
            throw rejected();
        }
        Matcher salt = SALT.matcher(solution.salt());
        if (!salt.matches()) {
            throw rejected();
        }
        Instant expires = Instant.ofEpochSecond(Long.parseLong(salt.group(1)));
        boolean signed = MessageDigest.isEqual(sign(solution.challenge()).getBytes(StandardCharsets.US_ASCII),
                solution.signature().getBytes(StandardCharsets.US_ASCII));
        boolean solved = solution.number() >= 0 && solution.number() <= properties.maxNumber()
                && sha256(solution.salt() + solution.number()).equals(solution.challenge());
        if (!signed || !solved || clock.instant().isAfter(expires)) {
            throw rejected();
        }
        int recorded = jdbc.update("""
                insert into used_register_challenges (challenge, expires_at) values (:challenge, :expires)
                on conflict (challenge) do nothing""",
                new MapSqlParameterSource("challenge", solution.challenge())
                        .addValue("expires", Timestamp.from(expires)));
        if (recorded == 0) {
            throw rejected();
        }
    }

    private DomainException rejected() {
        signals.record(SecuritySignals.Signal.CHALLENGE_REJECTED);
        return new DomainException(INVALID);
    }

    /** Minimização: depois de vencido, o desafio não serve para nada e o registro some. */
    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT25M")
    void purgeExpired() {
        jdbc.update("delete from used_register_challenges where expires_at < :now",
                new MapSqlParameterSource("now", Timestamp.from(clock.instant())));
    }

    private String sign(String challenge) {
        return HexFormat.of().formatHex(hmac(key, challenge));
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(ALGORITHM)
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM", e);
        }
    }

    private static byte[] hmac(SecretKeySpec key, String message) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(key);
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC indisponível na JVM", e);
        }
    }
}
