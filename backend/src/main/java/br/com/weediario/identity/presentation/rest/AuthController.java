package br.com.weediario.identity.presentation.rest;

import br.com.weediario.identity.application.exception.InvalidSessionException;
import br.com.weediario.identity.application.usecase.AccountSecurityUseCase;
import br.com.weediario.identity.application.usecase.AuthenticateUserUseCase;
import br.com.weediario.identity.application.usecase.LogoutUseCase;
import br.com.weediario.identity.application.usecase.PasswordResetUseCase;
import br.com.weediario.identity.application.usecase.RecoverAccountUseCase;
import br.com.weediario.identity.application.usecase.RefreshSessionUseCase;
import br.com.weediario.identity.application.usecase.RegisterUserUseCase;
import br.com.weediario.identity.presentation.rest.dto.AccessKeyLoginRequest;
import br.com.weediario.identity.presentation.rest.dto.AccessTokenResponse;
import br.com.weediario.identity.presentation.rest.dto.EmailVerifyRequest;
import br.com.weediario.identity.presentation.rest.dto.LoginRequest;
import br.com.weediario.identity.presentation.rest.dto.PasswordResetConfirmRequest;
import br.com.weediario.identity.presentation.rest.dto.PasswordResetRequest;
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
    private final PasswordResetUseCase passwordReset;
    private final AccountSecurityUseCase accountSecurity;
    private final RefreshTokenCookieFactory cookies;
    private final SessionResponses sessions;

    public AuthController(RegisterUserUseCase registerUser, AuthenticateUserUseCase authenticateUser,
                          RefreshSessionUseCase refreshSession, LogoutUseCase logout,
                          RecoverAccountUseCase recoverAccount, PasswordResetUseCase passwordReset,
                          AccountSecurityUseCase accountSecurity, RefreshTokenCookieFactory cookies,
                          SessionResponses sessions) {
        this.registerUser = registerUser;
        this.authenticateUser = authenticateUser;
        this.refreshSession = refreshSession;
        this.logout = logout;
        this.recoverAccount = recoverAccount;
        this.passwordReset = passwordReset;
        this.accountSecurity = accountSecurity;
        this.cookies = cookies;
        this.sessions = sessions;
    }

    /** Cria a conta e já entra. Se veio e-mail, o link de confirmação é enviado a ele. */
    @PostMapping("/register")
    public ResponseEntity<AccessTokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        return sessions.open(HttpStatus.CREATED, registerUser.execute(new RegisterUserUseCase.Command(
                request.username(), request.password(), request.email(), request.recoveryVerifier(),
                request.acceptedTermsVersion())));
    }

    @PostMapping("/login")
    public ResponseEntity<AccessTokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return sessions.open(authenticateUser.execute(
                new AuthenticateUserUseCase.Command(request.login(), request.password())));
    }

    /** Conta da época da chave de acesso: último login com a chave, criando a senha. */
    @PostMapping("/access-key-login")
    public ResponseEntity<AccessTokenResponse> accessKeyLogin(@Valid @RequestBody AccessKeyLoginRequest request) {
        return sessions.open(authenticateUser.loginWithAccessKey(new AuthenticateUserUseCase.AccessKeyCommand(
                request.login(), request.accessKey(), request.newPassword())));
    }

    /** Frase de recuperação: senha nova, frase nova, outras sessões encerradas, e já entra na conta. */
    @PostMapping("/recover")
    public ResponseEntity<AccessTokenResponse> recover(@Valid @RequestBody RecoverRequest request) {
        return sessions.open(recoverAccount.execute(new RecoverAccountUseCase.Command(request.username(),
                request.recoveryVerifier(), request.newPassword(), request.newRecoveryVerifier())));
    }

    /** Sempre 202, tenha a conta e-mail confirmado ou não. */
    @PostMapping("/password-reset")
    public ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        passwordReset.request(request.username());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<AccessTokenResponse> confirmPasswordReset(
            @Valid @RequestBody PasswordResetConfirmRequest request) {
        return sessions.open(passwordReset.confirm(request.token(), request.newPassword()));
    }

    /** Público: o link de confirmação pode ser aberto em outro aparelho, sem sessão. */
    @PostMapping("/email/verify")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody EmailVerifyRequest request) {
        accountSecurity.verifyEmail(request.token());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<AccessTokenResponse> refresh(HttpServletRequest request) {
        return sessions.open(refreshSession.execute(readRefreshCookie(request)));
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

    private String readRefreshCookie(HttpServletRequest request) {
        var cookie = WebUtils.getCookie(request, cookies.cookieName());
        return cookie == null ? null : cookie.getValue();
    }
}
