package br.com.weediario.identity.infrastructure.config;

import br.com.weediario.identity.application.CredentialCheck;
import br.com.weediario.identity.application.LoginThrottle;
import br.com.weediario.identity.application.SecurityNotifier;
import br.com.weediario.identity.application.SessionIssuer;
import br.com.weediario.identity.application.port.AccessTokenIssuer;
import br.com.weediario.identity.application.port.AccountMailer;
import br.com.weediario.identity.application.port.RegistrationChallenge;
import br.com.weediario.identity.application.port.AvatarImages;
import br.com.weediario.identity.application.port.BackgroundTasks;
import br.com.weediario.identity.application.port.LoginKeyHasher;
import br.com.weediario.identity.application.port.ProfileModeration;
import br.com.weediario.identity.application.port.RefreshTokenCodec;
import br.com.weediario.identity.application.usecase.AccountDataUseCase;
import br.com.weediario.identity.application.usecase.AccountSecurityUseCase;
import br.com.weediario.identity.application.usecase.AuthenticateUserUseCase;
import br.com.weediario.identity.application.usecase.ChangeRecoveryPhraseUseCase;
import br.com.weediario.identity.application.usecase.LogoutUseCase;
import br.com.weediario.identity.application.usecase.ManageProfileUseCase;
import br.com.weediario.identity.application.usecase.PasswordResetUseCase;
import br.com.weediario.identity.application.usecase.RecoverAccountUseCase;
import br.com.weediario.identity.application.usecase.RefreshSessionUseCase;
import br.com.weediario.identity.application.usecase.RegisterUserUseCase;
import br.com.weediario.identity.domain.model.SecurityEvent;
import br.com.weediario.identity.domain.repository.AccountEmailRepository;
import br.com.weediario.identity.domain.repository.AccountTokenRepository;
import br.com.weediario.identity.domain.repository.LoginAttemptsRepository;
import br.com.weediario.identity.domain.repository.RefreshTokenRepository;
import br.com.weediario.identity.domain.repository.SecurityEventRepository;
import br.com.weediario.identity.domain.repository.UserRepository;
import br.com.weediario.identity.domain.service.PasswordHasher;
import br.com.weediario.shared.application.ActionQuota;
import br.com.weediario.shared.application.PersonalDataSection;
import br.com.weediario.shared.application.UnitOfWork;
import br.com.weediario.shared.infrastructure.security.SecurityProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

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
    CredentialCheck credentialCheck(PasswordHasher hasher, LoginThrottle throttle) {
        return new CredentialCheck(hasher, throttle);
    }

    @Bean
    SecurityNotifier securityNotifier(SecurityEventRepository events, AccountEmailRepository emails,
                                      AccountMailer mailer, UnitOfWork unitOfWork, Clock clock) {
        return new SecurityNotifier(events, emails, mailer, unitOfWork, clock);
    }

    @Bean
    RegisterUserUseCase registerUserUseCase(UserRepository users, AccountEmailRepository emails,
                                            AccountTokenRepository tokens, PasswordHasher hasher,
                                            SessionIssuer sessionIssuer, AccountMailer mailer,
                                            RegistrationChallenge challenges, UnitOfWork unitOfWork, Clock clock) {
        return new RegisterUserUseCase(users, emails, tokens, hasher, sessionIssuer, mailer, challenges, unitOfWork,
                clock);
    }

    @Bean
    AuthenticateUserUseCase authenticateUserUseCase(UserRepository users, PasswordHasher hasher,
                                                    SessionIssuer sessionIssuer, LoginThrottle throttle,
                                                    UnitOfWork unitOfWork) {
        return new AuthenticateUserUseCase(users, hasher, sessionIssuer, throttle, unitOfWork);
    }

    @Bean
    RecoverAccountUseCase recoverAccountUseCase(UserRepository users, AccountTokenRepository tokens,
                                                PasswordHasher hasher, SessionIssuer sessionIssuer,
                                                RefreshTokenRepository refreshTokens, SecurityNotifier security,
                                                UnitOfWork unitOfWork, Clock clock) {
        return new RecoverAccountUseCase(users, tokens, hasher, sessionIssuer, refreshTokens, security, unitOfWork,
                clock);
    }

    @Bean
    PasswordResetUseCase passwordResetUseCase(UserRepository users, AccountEmailRepository emails,
                                              AccountTokenRepository tokens, RefreshTokenRepository refreshTokens,
                                              PasswordHasher hasher, SessionIssuer sessionIssuer, AccountMailer mailer,
                                              BackgroundTasks background, SecurityNotifier security,
                                              UnitOfWork unitOfWork, Clock clock) {
        return new PasswordResetUseCase(users, emails, tokens, refreshTokens, hasher, sessionIssuer, mailer, background,
                security, unitOfWork, clock);
    }

    @Bean
    AccountSecurityUseCase accountSecurityUseCase(UserRepository users, AccountEmailRepository emails,
                                                  AccountTokenRepository tokens, SecurityEventRepository events,
                                                  RefreshTokenRepository refreshTokens, PasswordHasher hasher,
                                                  CredentialCheck credentials, SessionIssuer sessionIssuer,
                                                  AccountMailer mailer, SecurityNotifier security,
                                                  ActionQuota quota, UnitOfWork unitOfWork, Clock clock) {
        return new AccountSecurityUseCase(users, emails, tokens, events, refreshTokens, hasher, credentials,
                sessionIssuer, mailer, security, quota, unitOfWork, clock);
    }

    /** As seções de dados pessoais de todos os contextos (cada uma é um componente na infraestrutura do seu contexto). */
    @Bean
    AccountDataUseCase accountDataUseCase(UserRepository users, AccountEmailRepository emails,
                                          SecurityEventRepository events, List<PersonalDataSection> sections,
                                          CredentialCheck credentials, AccountMailer mailer,
                                          ActionQuota quota, UnitOfWork unitOfWork,
                                          Clock clock) {
        return new AccountDataUseCase(users, emails, events, sections, credentials, mailer, quota, unitOfWork, clock);
    }

    @Bean
    ChangeRecoveryPhraseUseCase changeRecoveryPhraseUseCase(UserRepository users, PasswordHasher hasher,
                                                            CredentialCheck credentials, SecurityNotifier security,
                                                            UnitOfWork unitOfWork, Clock clock) {
        return new ChangeRecoveryPhraseUseCase(users, hasher, credentials, security, unitOfWork, clock);
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

    @Bean
    AccountHousekeeping accountHousekeeping(AccountTokenRepository tokens, SecurityEventRepository events,
                                            Clock clock) {
        return new AccountHousekeeping(tokens, events, clock);
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

    /**
     * Minimização de dados: links de e-mail vencidos somem no dia seguinte e o histórico de segurança é guardado por
     * {@value SecurityEvent#RETENTION_DAYS} dias.
     */
    public static class AccountHousekeeping {

        private final AccountTokenRepository tokens;
        private final SecurityEventRepository events;
        private final Clock clock;

        AccountHousekeeping(AccountTokenRepository tokens, SecurityEventRepository events, Clock clock) {
            this.tokens = tokens;
            this.events = events;
            this.clock = clock;
        }

        @Scheduled(fixedDelayString = "PT6H", initialDelayString = "PT20M")
        @Transactional
        public void purge() {
            tokens.deleteExpiredBefore(clock.instant().minus(Duration.ofDays(1)));
            events.deleteBefore(clock.instant().minus(Duration.ofDays(SecurityEvent.RETENTION_DAYS)));
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
