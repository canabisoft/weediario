package br.com.weediario.identity.infrastructure.config;

import br.com.weediario.identity.application.LoginThrottle;
import br.com.weediario.identity.application.SessionIssuer;
import br.com.weediario.identity.application.port.AccessTokenIssuer;
import br.com.weediario.identity.application.port.AvatarImages;
import br.com.weediario.identity.application.port.ProfileModeration;
import br.com.weediario.identity.application.port.LoginKeyHasher;
import br.com.weediario.identity.application.port.RefreshTokenCodec;
import br.com.weediario.identity.application.usecase.AuthenticateUserUseCase;
import br.com.weediario.identity.application.usecase.ChangeRecoveryPhraseUseCase;
import br.com.weediario.identity.application.usecase.LogoutUseCase;
import br.com.weediario.identity.application.usecase.ManageProfileUseCase;
import br.com.weediario.identity.application.usecase.RecoverAccountUseCase;
import br.com.weediario.identity.application.usecase.RefreshSessionUseCase;
import br.com.weediario.identity.application.usecase.RegisterUserUseCase;
import br.com.weediario.identity.domain.repository.LoginAttemptsRepository;
import br.com.weediario.identity.domain.repository.RefreshTokenRepository;
import br.com.weediario.identity.domain.repository.UserRepository;
import br.com.weediario.identity.domain.service.PasswordHasher;
import br.com.weediario.shared.application.UnitOfWork;
import br.com.weediario.shared.infrastructure.security.SecurityProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;

/**
 * Os casos de uso são POJOs sem anotações de framework; o Spring só os instancia aqui.
 */
@Configuration
public class IdentityUseCaseConfig {

    @Bean
    SessionIssuer sessionIssuer(AccessTokenIssuer accessTokenIssuer, RefreshTokenCodec refreshTokenCodec,
                                RefreshTokenRepository refreshTokens, SecurityProperties properties, Clock clock) {
        return new SessionIssuer(accessTokenIssuer, refreshTokenCodec, refreshTokens,
                properties.refreshToken().ttl(), clock);
    }

    @Bean
    LoginThrottle loginThrottle(LoginAttemptsRepository attempts, LoginKeyHasher keyHasher, UnitOfWork unitOfWork,
                                Clock clock) {
        return new LoginThrottle(attempts, keyHasher, unitOfWork, clock);
    }

    @Bean
    RegisterUserUseCase registerUserUseCase(UserRepository users, PasswordHasher hasher, SessionIssuer sessionIssuer,
                                            UnitOfWork unitOfWork, Clock clock) {
        return new RegisterUserUseCase(users, hasher, sessionIssuer, unitOfWork, clock);
    }

    @Bean
    AuthenticateUserUseCase authenticateUserUseCase(UserRepository users, PasswordHasher hasher,
                                                    SessionIssuer sessionIssuer, LoginThrottle throttle,
                                                    UnitOfWork unitOfWork, Clock clock) {
        return new AuthenticateUserUseCase(users, hasher, sessionIssuer, throttle, unitOfWork, clock);
    }

    @Bean
    RecoverAccountUseCase recoverAccountUseCase(UserRepository users, PasswordHasher hasher,
                                                SessionIssuer sessionIssuer, RefreshTokenRepository refreshTokens,
                                                LoginThrottle throttle, UnitOfWork unitOfWork, Clock clock) {
        return new RecoverAccountUseCase(users, hasher, sessionIssuer, refreshTokens, throttle, unitOfWork, clock);
    }

    @Bean
    ChangeRecoveryPhraseUseCase changeRecoveryPhraseUseCase(UserRepository users, PasswordHasher hasher,
                                                            LoginThrottle throttle, UnitOfWork unitOfWork, Clock clock) {
        return new ChangeRecoveryPhraseUseCase(users, hasher, throttle, unitOfWork, clock);
    }

    @Bean
    ManageProfileUseCase manageProfileUseCase(UserRepository users, AvatarImages avatars, ProfileModeration moderation,
                                              UnitOfWork unitOfWork, Clock clock) {
        return new ManageProfileUseCase(users, avatars, moderation, unitOfWork, clock);
    }

    @Bean
    StaleLoginAttemptsJanitor staleLoginAttemptsJanitor(LoginAttemptsRepository attempts, Clock clock) {
        return new StaleLoginAttemptsJanitor(attempts, clock);
    }

    /** Minimização de dados: registros de tentativas sem atividade há um dia são apagados. */
    static class StaleLoginAttemptsJanitor {

        private final LoginAttemptsRepository attempts;
        private final Clock clock;

        StaleLoginAttemptsJanitor(LoginAttemptsRepository attempts, Clock clock) {
            this.attempts = attempts;
            this.clock = clock;
        }

        @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT10M")
        @Transactional
        void purge() {
            attempts.deleteStaleBefore(clock.instant().minus(Duration.ofDays(1)));
        }
    }

    @Bean
    RefreshSessionUseCase refreshSessionUseCase(RefreshTokenRepository refreshTokens, UserRepository users,
                                                RefreshTokenCodec refreshTokenCodec, SessionIssuer sessionIssuer,
                                                UnitOfWork unitOfWork, Clock clock, SecurityProperties properties) {
        return new RefreshSessionUseCase(refreshTokens, users, refreshTokenCodec, sessionIssuer, unitOfWork, clock,
                properties.refreshToken().reuseGrace());
    }

    @Bean
    LogoutUseCase logoutUseCase(RefreshTokenRepository refreshTokens, RefreshTokenCodec refreshTokenCodec,
                                UnitOfWork unitOfWork, Clock clock) {
        return new LogoutUseCase(refreshTokens, refreshTokenCodec, unitOfWork, clock);
    }
}
