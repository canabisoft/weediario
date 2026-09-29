package br.com.weediario.identity.presentation.rest;

import br.com.weediario.identity.application.usecase.ManageProfileUseCase;
import br.com.weediario.identity.application.usecase.ManageProfileUseCase.MyProfile;
import br.com.weediario.identity.domain.model.User;
import br.com.weediario.shared.application.ResourceNotFoundException;
import br.com.weediario.shared.presentation.Viewer;
import jakarta.validation.Valid;
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

    public ProfileController(ManageProfileUseCase profiles) {
        this.profiles = profiles;
    }

    @GetMapping("/api/v1/me")
    public MyProfile me(@AuthenticationPrincipal Jwt principal) {
        return profiles.me(Viewer.requireId(principal));
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
        byte[] content = profiles.avatarOf(username).orElseThrow(() -> new ResourceNotFoundException("Foto"));
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .body(content);
    }

    public record ProfileRequest(@Size(max = User.BIO_MAX_LENGTH) String bio) {
    }

    public record AvatarRequest(@NotNull UUID imageId) {
    }
}
