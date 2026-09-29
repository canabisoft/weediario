package br.com.weediario.identity.infrastructure.config;

import br.com.weediario.identity.application.SessionIssuer;
import br.com.weediario.identity.application.port.AccessTokenIssuer;
import br.com.weediario.identity.application.port.RefreshTokenCodec;
import br.com.weediario.identity.application.usecase.AuthenticateUserUseCase;
import br.com.weediario.identity.application.usecase.LogoutUseCase;
import br.com.weediario.identity.application.usecase.RefreshSessionUseCase;
import br.com.weediario.identity.application.usecase.RegisterUserUseCase;
import br.com.weediario.identity.domain.repository.RefreshTokenRepository;
import br.com.weediario.identity.domain.repository.UserRepository;
import br.com.weediario.identity.domain.service.PasswordHasher;
import br.com.weediario.shared.application.UnitOfWork;
import br.com.weediario.shared.infrastructure.security.SecurityProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

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
    RegisterUserUseCase registerUserUseCase(UserRepository users, PasswordHasher passwordHasher,
                                            UnitOfWork unitOfWork, Clock clock) {
        return new RegisterUserUseCase(users, passwordHasher, unitOfWork, clock);
    }

    @Bean
    AuthenticateUserUseCase authenticateUserUseCase(UserRepository users, PasswordHasher passwordHasher,
                                                    SessionIssuer sessionIssuer, UnitOfWork unitOfWork) {
        return new AuthenticateUserUseCase(users, passwordHasher, sessionIssuer, unitOfWork);
    }

    @Bean
    RefreshSessionUseCase refreshSessionUseCase(RefreshTokenRepository refreshTokens, UserRepository users,
                                                RefreshTokenCodec refreshTokenCodec, SessionIssuer sessionIssuer,
                                                UnitOfWork unitOfWork, Clock clock) {
        return new RefreshSessionUseCase(refreshTokens, users, refreshTokenCodec, sessionIssuer, unitOfWork, clock);
    }

    @Bean
    LogoutUseCase logoutUseCase(RefreshTokenRepository refreshTokens, RefreshTokenCodec refreshTokenCodec,
                                UnitOfWork unitOfWork, Clock clock) {
        return new LogoutUseCase(refreshTokens, refreshTokenCodec, unitOfWork, clock);
    }
}
