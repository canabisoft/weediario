package br.com.weediario.identity.presentation.rest;

import br.com.weediario.identity.application.port.AvatarImages.AvatarFile;
import br.com.weediario.identity.application.usecase.AccountSecurityUseCase;
import br.com.weediario.identity.application.usecase.ChangeRecoveryPhraseUseCase;
import br.com.weediario.identity.application.usecase.ManageProfileUseCase;
import br.com.weediario.identity.application.usecase.ManageProfileUseCase.MyProfile;
import br.com.weediario.identity.domain.model.EmailAddress;
import br.com.weediario.identity.domain.model.RecoveryVerifier;
import br.com.weediario.identity.domain.model.User;
import br.com.weediario.identity.domain.service.PasswordPolicy;
import br.com.weediario.identity.presentation.rest.dto.AccessTokenResponse;
import br.com.weediario.shared.application.ResourceNotFoundException;
import br.com.weediario.shared.presentation.Viewer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.UUID;

@RestController
public class ProfileController {

    private final ManageProfileUseCase profiles;
    private final ChangeRecoveryPhraseUseCase recoveryPhrase;
    private final AccountSecurityUseCase accountSecurity;
    private final SessionResponses sessions;

    public ProfileController(ManageProfileUseCase profiles, ChangeRecoveryPhraseUseCase recoveryPhrase,
                             AccountSecurityUseCase accountSecurity, SessionResponses sessions) {
        this.profiles = profiles;
        this.recoveryPhrase = recoveryPhrase;
        this.accountSecurity = accountSecurity;
        this.sessions = sessions;
    }

    @GetMapping("/api/v1/me")
    public MyProfile me(@AuthenticationPrincipal Jwt principal) {
        return profiles.me(Viewer.requireId(principal));
    }

    /** Aceite da versão vigente dos Termos de Uso (contas antigas aceitam antes de voltar a publicar). */
    @PutMapping("/api/v1/me/terms")
    public MyProfile acceptTerms(@AuthenticationPrincipal Jwt principal, @Valid @RequestBody TermsRequest request) {
        return profiles.acceptTerms(Viewer.requireId(principal), request.version());
    }

    /** Gera (contas antigas) ou troca a frase de recuperação. Só o derivado da frase chega aqui, nunca as palavras. */
    @PutMapping("/api/v1/me/recovery")
    public MyProfile changeRecovery(@AuthenticationPrincipal Jwt principal, @Valid @RequestBody RecoveryRequest request) {
        UUID userId = Viewer.requireId(principal);
        recoveryPhrase.execute(userId, request.recoveryVerifier(), request.currentPassword());
        return profiles.me(userId);
    }

    /** Senha, e-mail (mascarado) e histórico de mudanças sensíveis dos últimos 90 dias. */
    @GetMapping("/api/v1/me/security")
    public ResponseEntity<AccountSecurityUseCase.Overview> security(@AuthenticationPrincipal Jwt principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(accountSecurity.overview(Viewer.requireId(principal)));
    }

    /** Troca a senha; todas as sessões caem e uma nova é aberta para este aparelho. */
    @PutMapping("/api/v1/me/password")
    public ResponseEntity<AccessTokenResponse> changePassword(@AuthenticationPrincipal Jwt principal,
                                                              @Valid @RequestBody PasswordChangeRequest request) {
        return sessions.open(accountSecurity.changePassword(Viewer.requireId(principal), request.currentPassword(),
                request.newPassword()));
    }

    /** Cadastra, troca ou (com e-mail vazio) remove o e-mail. */
    @PutMapping("/api/v1/me/email")
    public ResponseEntity<AccountSecurityUseCase.Overview> changeEmail(@AuthenticationPrincipal Jwt principal,
                                                                       @Valid @RequestBody EmailChangeRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(accountSecurity.changeEmail(Viewer.requireId(principal), request.currentPassword(),
                        request.email()));
    }

    @PostMapping("/api/v1/me/email/resend")
    public ResponseEntity<Void> resendVerification(@AuthenticationPrincipal Jwt principal) {
        accountSecurity.resendVerification(Viewer.requireId(principal));
        return ResponseEntity.accepted().build();
    }

    @PutMapping("/api/v1/me/profile")
    public MyProfile updateProfile(@AuthenticationPrincipal Jwt principal, @Valid @RequestBody ProfileRequest request) {
        return profiles.updateBio(Viewer.requireId(principal), request.bio());
    }

    /** A foto é enviada antes em {@code POST /api/v1/media/images} (sanitizada, sem EXIF); aqui só o id. */
    @PutMapping("/api/v1/me/avatar")
    public MyProfile changeAvatar(@AuthenticationPrincipal Jwt principal, @Valid @RequestBody AvatarRequest request) {
        return profiles.changeAvatar(Viewer.requireId(principal), request.imageId());
    }

    @DeleteMapping("/api/v1/me/avatar")
    public MyProfile removeAvatar(@AuthenticationPrincipal Jwt principal) {
        return profiles.changeAvatar(Viewer.requireId(principal), null);
    }

    @GetMapping("/api/v1/growers/{username}/avatar")
    public ResponseEntity<byte[]> avatar(@PathVariable String username) {
        AvatarFile avatar = profiles.avatarOf(username).orElseThrow(() -> new ResourceNotFoundException("Foto"));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(avatar.mediaType()))
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .body(avatar.content());
    }

    public record ProfileRequest(@Size(max = User.BIO_MAX_LENGTH) String bio) {
    }

    public record AvatarRequest(@NotNull UUID imageId) {
    }

    public record TermsRequest(@NotBlank @Size(max = 10) String version) {
    }

    /** @param currentPassword senha atual (ou, em conta que ainda não criou senha, a chave de acesso antiga) */
    public record RecoveryRequest(
            @NotBlank @Size(max = RecoveryVerifier.LENGTH) String recoveryVerifier,
            @NotBlank @Size(max = PasswordPolicy.MAX_LENGTH) String currentPassword) {

        @Override
        public String toString() {
            return "RecoveryRequest[***]";
        }
    }

    public record PasswordChangeRequest(
            @NotBlank @Size(max = PasswordPolicy.MAX_LENGTH) String currentPassword,
            @NotBlank @Size(max = PasswordPolicy.MAX_LENGTH) String newPassword) {

        @Override
        public String toString() {
            return "PasswordChangeRequest[***]";
        }
    }

    /** @param email vazio ou nulo remove o e-mail da conta */
    public record EmailChangeRequest(
            @NotBlank @Size(max = PasswordPolicy.MAX_LENGTH) String currentPassword,
            @Size(max = EmailAddress.MAX_LENGTH) String email) {

        @Override
        public String toString() {
            return "EmailChangeRequest[***]";
        }
    }
}
