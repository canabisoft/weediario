package br.com.weediario.identity.infrastructure.security;

import br.com.weediario.identity.application.port.RegistrationChallenge.Challenge;
import br.com.weediario.identity.application.port.RegistrationChallenge.Solution;
import br.com.weediario.shared.domain.DomainException;
import br.com.weediario.shared.infrastructure.observability.SecuritySignals;
import org.junit.jupiter.api.Test;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Conferências feitas antes de tocar no banco: aqui não há banco nenhum. */
class HmacRegistrationChallengeTest {

    private static final SecretKeySpec KEY = new SecretKeySpec(new byte[32], "HmacSHA256");
    private static final Instant NOW = Instant.parse("2026-10-06T12:00:00Z");
    private static final RegistrationChallengeProperties PROPERTIES =
            new RegistrationChallengeProperties(50L, Duration.ofMinutes(10));

    private final SecuritySignals signals = new SecuritySignals();

    @Test
    void expiredSolutionIsRefused() throws Exception {
        Solution solution = solve(at(KEY, NOW).issue());

        assertThatThrownBy(() -> at(KEY, NOW.plus(Duration.ofMinutes(11))).redeem(solution))
                .isInstanceOf(DomainException.class);
        assertThat(signals.count(SecuritySignals.Signal.CHALLENGE_REJECTED)).isEqualTo(1);
    }

    /** O prazo vai dentro do salt, coberto pelo hash: esticar o prazo invalida a solução. */
    @Test
    void extendingTheDeadlineInTheSaltBreaksTheSolution() throws Exception {
        HmacRegistrationChallenge challenges = at(KEY, NOW);
        Solution solution = solve(challenges.issue());
        String stretched = solution.salt().replaceAll("expires=\\d+",
                "expires=" + NOW.plus(Duration.ofDays(365)).getEpochSecond());

        assertThatThrownBy(() -> challenges.redeem(new Solution(solution.challenge(), stretched, solution.number(),
                solution.signature()))).isInstanceOf(DomainException.class);
    }

    @Test
    void anotherServerKeyDoesNotAcceptTheSignature() throws Exception {
        Solution solution = solve(at(KEY, NOW).issue());
        byte[] other = new byte[32];
        other[0] = 1;

        assertThatThrownBy(() -> at(new SecretKeySpec(other, "HmacSHA256"), NOW).redeem(solution))
                .isInstanceOf(DomainException.class);
    }

    private HmacRegistrationChallenge at(SecretKeySpec key, Instant now) {
        return new HmacRegistrationChallenge(key, PROPERTIES, null, Clock.fixed(now, ZoneOffset.UTC), signals);
    }

    private static Solution solve(Challenge challenge) throws Exception {
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        for (long number = 0; number <= challenge.maxNumber(); number++) {
            String hash = HexFormat.of().formatHex(
                    sha256.digest((challenge.salt() + number).getBytes(StandardCharsets.UTF_8)));
            if (hash.equals(challenge.challenge())) {
                return new Solution(challenge.challenge(), challenge.salt(), number, challenge.signature());
            }
        }
        throw new IllegalStateException("Desafio sem solução");
    }
}
