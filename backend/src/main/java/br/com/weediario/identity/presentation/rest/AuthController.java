package br.com.weediario.identity.presentation.rest;

import br.com.weediario.identity.application.AuthenticatedSession;
import br.com.weediario.identity.application.exception.InvalidSessionException;
import br.com.weediario.identity.application.usecase.AuthenticateUserUseCase;
import br.com.weediario.identity.application.usecase.LogoutUseCase;
import br.com.weediario.identity.application.usecase.RecoverAccountUseCase;
import br.com.weediario.identity.application.usecase.RefreshSessionUseCase;
import br.com.weediario.identity.application.usecase.RegisterUserUseCase;
import br.com.weediario.identity.presentation.rest.dto.AccessTokenResponse;
import br.com.weediario.identity.presentation.rest.dto.LoginRequest;
import br.com.weediario.identity.presentation.rest.dto.RecoverRequest;
import br.com.weediario.identity.presentation.rest.dto.RegisterRequest;
import br.com.weediario.shared.infrastructure.security.SecurityConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.WebUtils;

@RestController
@RequestMapping(SecurityConfig.AUTH_PATH)
public class AuthController {

    private final RegisterUserUseCase registerUser;
    private final AuthenticateUserUseCase authenticateUser;
    private final RefreshSessionUseCase refreshSession;
    private final LogoutUseCase logout;
    private final RecoverAccountUseCase recoverAccount;
    private final RefreshTokenCookieFactory cookies;

    public AuthController(RegisterUserUseCase registerUser, AuthenticateUserUseCase authenticateUser,
                          RefreshSessionUseCase refreshSession, LogoutUseCase logout,
                          RecoverAccountUseCase recoverAccount, RefreshTokenCookieFactory cookies) {
        this.registerUser = registerUser;
        this.authenticateUser = authenticateUser;
        this.refreshSession = refreshSession;
        this.logout = logout;
        this.recoverAccount = recoverAccount;
        this.cookies = cookies;
    }

    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request) {
        registerUser.execute(new RegisterUserUseCase.Command(request.username(), request.password(),
                request.recoveryVerifier(), request.acceptedTermsVersion()));
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/login")
    public ResponseEntity<AccessTokenResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthenticatedSession session = authenticateUser.execute(
                new AuthenticateUserUseCase.Command(request.login(), request.password()));
        return withSession(session);
    }

    /** Frase de recuperação + senha nova: encerra as outras sessões e já entra na conta. */
    @PostMapping("/recover")
    public ResponseEntity<AccessTokenResponse> recover(@Valid @RequestBody RecoverRequest request) {
        return withSession(recoverAccount.execute(new RecoverAccountUseCase.Command(
                request.username(), request.recoveryVerifier(), request.newPassword())));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AccessTokenResponse> refresh(HttpServletRequest request) {
        return withSession(refreshSession.execute(readRefreshCookie(request)));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        logout.execute(readRefreshCookie(request));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.expire().toString())
                .build();
    }

    /** Sessão inválida: além do 401, apaga o cookie para o cliente não ficar tentando um token morto. */
    @ExceptionHandler(InvalidSessionException.class)
    ResponseEntity<ProblemDetail> handleInvalidSession(InvalidSessionException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.SET_COOKIE, cookies.expire().toString())
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage()));
    }

    private ResponseEntity<AccessTokenResponse> withSession(AuthenticatedSession session) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        cookies.create(session.refreshToken(), session.refreshTokenExpiresAt()).toString())
                .body(AccessTokenResponse.bearer(session.accessToken(), session.accessTokenExpiresAt()));
    }

    private String readRefreshCookie(HttpServletRequest request) {
        var cookie = WebUtils.getCookie(request, cookies.cookieName());
        return cookie == null ? null : cookie.getValue();
    }
}
