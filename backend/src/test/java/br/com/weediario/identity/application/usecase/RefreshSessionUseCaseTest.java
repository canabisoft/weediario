package br.com.weediario.identity.application.usecase;

import br.com.weediario.identity.application.AuthenticatedSession;
import br.com.weediario.identity.application.SessionIssuer;
import br.com.weediario.identity.application.exception.InvalidSessionException;
import br.com.weediario.identity.application.port.AccessTokenIssuer;
import br.com.weediario.identity.application.port.RefreshTokenCodec;
import br.com.weediario.identity.domain.model.AccessKey;
import br.com.weediario.identity.domain.model.PasswordHash;
import br.com.weediario.identity.domain.model.RefreshToken;
import br.com.weediario.identity.domain.model.TermsOfUse;
import br.com.weediario.identity.domain.model.User;
import br.com.weediario.identity.domain.model.UserId;
import br.com.weediario.identity.domain.model.Username;
import br.com.weediario.identity.domain.repository.RefreshTokenRepository;
import br.com.weediario.identity.domain.repository.UserRepository;
import br.com.weediario.shared.application.UnitOfWork;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefreshSessionUseCaseTest {

    private static final Duration REUSE_GRACE = Duration.ofSeconds(30);

    private final MutableClock clock = new MutableClock(Instant.parse("2026-09-29T12:00:00Z"));
    private final InMemoryRefreshTokens refreshTokens = new InMemoryRefreshTokens();
    private final InMemoryUsers users = new InMemoryUsers();
    private final RefreshTokenCodec codec = new CountingCodec();
    private final UnitOfWork unitOfWork = new UnitOfWork() {
        @Override
        public <T> T execute(Supplier<T> work) {
            return work.get();
        }
    };
    private final AccessTokenIssuer accessTokens =
            user -> new AccessTokenIssuer.IssuedAccessToken("access-" + user.id(), clock.instant().plusSeconds(600));
    private final SessionIssuer sessionIssuer =
            new SessionIssuer(accessTokens, codec, refreshTokens, Duration.ofDays(7), clock);
    private final RefreshSessionUseCase useCase =
            new RefreshSessionUseCase(refreshTokens, users, codec, sessionIssuer, unitOfWork, clock, REUSE_GRACE);

    private User user;

    @BeforeEach
    void setUp() {
        user = users.save(User.register(new Username("grower_anon"), AccessKey.generate().hash(),
                new PasswordHash("$argon2id$recovery"), TermsOfUse.CURRENT_VERSION, clock));
    }

    @Test
    void rotatesRefreshToken() {
        AuthenticatedSession first = sessionIssuer.startSession(user);

        AuthenticatedSession second = useCase.execute(first.refreshToken());

        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
        assertThat(refreshTokens.findByTokenHash(codec.hash(first.refreshToken()))).get()
                .extracting(RefreshToken::isRevoked).isEqualTo(true);
    }

    @Test
    void reuseOfRotatedTokenRevokesWholeFamily() {
        AuthenticatedSession first = sessionIssuer.startSession(user);
        AuthenticatedSession second = useCase.execute(first.refreshToken());
        clock.advance(REUSE_GRACE.plusSeconds(1));

        assertThatThrownBy(() -> useCase.execute(first.refreshToken())).isInstanceOf(InvalidSessionException.class);
        assertThatThrownBy(() -> useCase.execute(second.refreshToken())).isInstanceOf(InvalidSessionException.class);
    }

    @Test
    void reuseRightAfterRotationIsTreatedAsRaceAndKeepsSession() {
        // F5 que aborta a página depois de o servidor rotacionar, mas antes de o navegador gravar o cookie novo.
        AuthenticatedSession first = sessionIssuer.startSession(user);
        useCase.execute(first.refreshToken());
        clock.advance(REUSE_GRACE);

        AuthenticatedSession retried = useCase.execute(first.refreshToken());

        assertThat(retried.refreshToken()).isNotEqualTo(first.refreshToken());
        assertThat(useCase.execute(retried.refreshToken()).accessToken()).isNotBlank();
    }

    @Test
    void reuseAfterLogoutIsRejectedEvenWithinGrace() {
        AuthenticatedSession first = sessionIssuer.startSession(user);
        useCase.execute(first.refreshToken());
        refreshTokens.revokeFamily(
                refreshTokens.findByTokenHash(codec.hash(first.refreshToken())).orElseThrow().familyId(),
                clock.instant());

        assertThatThrownBy(() -> useCase.execute(first.refreshToken())).isInstanceOf(InvalidSessionException.class);
    }

    @Test
    void rejectsUnknownToken() {
        assertThatThrownBy(() -> useCase.execute("nao-existe")).isInstanceOf(InvalidSessionException.class);
        assertThatThrownBy(() -> useCase.execute(null)).isInstanceOf(InvalidSessionException.class);
    }

    @Test
    void rejectsSuspendedUser() {
        AuthenticatedSession session = sessionIssuer.startSession(user);
        user.suspend(clock);
        users.save(user);

        assertThatThrownBy(() -> useCase.execute(session.refreshToken())).isInstanceOf(InvalidSessionException.class);
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private static final class CountingCodec implements RefreshTokenCodec {
        private final AtomicInteger sequence = new AtomicInteger();

        @Override
        public String generate() {
            return "raw-" + sequence.incrementAndGet();
        }

        @Override
        public String hash(String rawToken) {
            return "hash-" + rawToken;
        }
    }

    private static final class InMemoryRefreshTokens implements RefreshTokenRepository {
        private final Map<UUID, RefreshToken> store = new HashMap<>();

        @Override
        public RefreshToken save(RefreshToken token) {
            store.put(token.id(), token);
            return token;
        }

        @Override
        public Optional<RefreshToken> findByTokenHash(String tokenHash) {
            return store.values().stream().filter(t -> t.tokenHash().equals(tokenHash)).findFirst();
        }

        @Override
        public void revokeFamily(UUID familyId, Instant now) {
            store.values().stream().filter(t -> t.familyId().equals(familyId)).forEach(t -> t.revoke(now));
        }

        @Override
        public void revokeAllOfUser(UUID userId, Instant now) {
            store.values().stream().filter(t -> t.userId().value().equals(userId)).forEach(t -> t.revoke(now));
        }

        @Override
        public boolean hasActiveToken(UUID familyId, Instant now) {
            return store.values().stream()
                    .anyMatch(t -> t.familyId().equals(familyId) && !t.isRevoked() && !t.isExpired(now));
        }
    }

    private static final class InMemoryUsers implements UserRepository {
        private final Map<UserId, User> store = new HashMap<>();

        @Override
        public User save(User user) {
            store.put(user.id(), user);
            return user;
        }

        @Override
        public Optional<User> findById(UserId id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Optional<User> findByUsername(String username) {
            return store.values().stream().filter(u -> u.username().value().equalsIgnoreCase(username)).findFirst();
        }

        @Override
        public boolean existsByUsername(Username username) {
            return store.values().stream().anyMatch(u -> u.username().value().equalsIgnoreCase(username.value()));
        }
    }
}
