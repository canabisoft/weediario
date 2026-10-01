package br.com.weediario.identity.presentation.rest;

import br.com.weediario.identity.application.port.AvatarImages.AvatarFile;
import br.com.weediario.identity.application.usecase.ChangeRecoveryPhraseUseCase;
import br.com.weediario.identity.application.usecase.ManageProfileUseCase;
import br.com.weediario.identity.application.usecase.ManageProfileUseCase.MyProfile;
import br.com.weediario.identity.domain.model.AccessKey;
import br.com.weediario.identity.domain.model.RecoveryVerifier;
import br.com.weediario.identity.domain.model.User;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.UUID;

@RestController
public class ProfileController {

    private final ManageProfileUseCase profiles;
    private final ChangeRecoveryPhraseUseCase recoveryPhrase;

    public ProfileController(ManageProfileUseCase profiles, ChangeRecoveryPhraseUseCase recoveryPhrase) {
        this.profiles = profiles;
        this.recoveryPhrase = recoveryPhrase;
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
        recoveryPhrase.execute(userId, request.recoveryVerifier(), request.currentAccessKey());
        return profiles.me(userId);
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

    public record RecoveryRequest(
            @NotBlank @Size(max = RecoveryVerifier.LENGTH) String recoveryVerifier,
            @NotBlank @Size(max = AccessKey.LENGTH) String currentAccessKey) {

        @Override
        public String toString() {
            return "RecoveryRequest[***]";
        }
    }
}
