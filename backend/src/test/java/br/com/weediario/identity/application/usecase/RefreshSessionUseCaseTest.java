package br.com.weediario.identity.application.usecase;

import br.com.weediario.identity.application.AuthenticatedSession;
import br.com.weediario.identity.application.SessionIssuer;
import br.com.weediario.identity.application.exception.InvalidSessionException;
import br.com.weediario.identity.application.port.AccessTokenIssuer;
import br.com.weediario.identity.application.port.RefreshTokenCodec;
import br.com.weediario.identity.domain.model.Email;
import br.com.weediario.identity.domain.model.PasswordHash;
import br.com.weediario.identity.domain.model.RefreshToken;
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

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-29T12:00:00Z"), ZoneOffset.UTC);
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
            new RefreshSessionUseCase(refreshTokens, users, codec, sessionIssuer, unitOfWork, clock);

    private User user;

    @BeforeEach
    void setUp() {
        user = users.save(User.register(new Username("grower_anon"), new Email("anon@example.org"),
                new PasswordHash("$argon2id$fake"), clock));
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

        assertThatThrownBy(() -> useCase.execute(first.refreshToken())).isInstanceOf(InvalidSessionException.class);
        assertThatThrownBy(() -> useCase.execute(second.refreshToken())).isInstanceOf(InvalidSessionException.class);
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
        public Optional<User> findByEmail(Email email) {
            return store.values().stream().filter(u -> u.email().equals(email)).findFirst();
        }

        @Override
        public boolean existsByUsername(Username username) {
            return store.values().stream().anyMatch(u -> u.username().value().equalsIgnoreCase(username.value()));
        }

        @Override
        public boolean existsByEmail(Email email) {
            return findByEmail(email).isPresent();
        }
    }
}
