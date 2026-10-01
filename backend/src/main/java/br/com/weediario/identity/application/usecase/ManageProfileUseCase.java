package br.com.weediario.identity.application.usecase;

import br.com.weediario.identity.application.port.ProfileModeration;
import br.com.weediario.identity.domain.model.TermsOfUse;
import br.com.weediario.shared.domain.CommerceFilter;
import br.com.weediario.shared.domain.CommerceFilter.Verdict;
import java.time.Instant;

import br.com.weediario.identity.application.port.AvatarImages;
import br.com.weediario.identity.application.port.AvatarImages.AvatarFile;
import br.com.weediario.identity.domain.model.User;
import br.com.weediario.identity.domain.model.UserId;
import br.com.weediario.identity.domain.repository.UserRepository;
import br.com.weediario.shared.application.ResourceNotFoundException;
import br.com.weediario.shared.application.UnitOfWork;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/**
 * Perfil do próprio grower (bio e foto) e entrega pública da foto de perfil.
 */
public final class ManageProfileUseCase {

    private final UserRepository users;
    private final AvatarImages avatars;
    private final ProfileModeration moderation;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public ManageProfileUseCase(UserRepository users, AvatarImages avatars, ProfileModeration moderation,
                                UnitOfWork unitOfWork, Clock clock) {
        this.users = users;
        this.avatars = avatars;
        this.moderation = moderation;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    public MyProfile me(UUID userId) {
        return unitOfWork.execute(() -> MyProfile.of(load(userId)));
    }

    /** A bio é pública: passa pelo filtro anti-comércio (bloqueia contato/PIX; anúncio vai para análise). */
    public MyProfile updateBio(UUID userId, String bio) {
        Verdict verdict = CommerceFilter.requireAllowedOrHeld(bio);
        return unitOfWork.execute(() -> {
            User user = load(userId);
            user.updateBio(bio, clock);
            if (verdict.held()) {
                user.hideProfile(true, clock);
            }
            MyProfile profile = MyProfile.of(users.save(user));
            if (verdict.held()) {
                moderation.heldByFilter(userId, verdict.reason());
            }
            return profile;
        });
    }

    /** Aceite (ou novo aceite) da versão vigente dos Termos de Uso: guarda só versão e data/hora. */
    public MyProfile acceptTerms(UUID userId, String version) {
        return unitOfWork.execute(() -> {
            User user = load(userId);
            user.acceptTerms(version, clock);
            return MyProfile.of(users.save(user));
        });
    }

    /**
     * @param imageId imagem já enviada em {@code POST /api/v1/media/images}; {@code null} remove a foto
     */
    public MyProfile changeAvatar(UUID userId, UUID imageId) {
        Holder result = unitOfWork.execute(() -> {
            User user = load(userId);
            if (imageId != null) {
                avatars.attach(userId, imageId);
            }
            Optional<UUID> previous = user.replaceAvatar(imageId, clock);
            return new Holder(MyProfile.of(users.save(user)), previous);
        });
        // A foto antiga só é apagada depois do commit, para nunca deixar o perfil apontando para um arquivo inexistente.
        result.previous().ifPresent(avatars::discard);
        return result.profile();
    }

    public Optional<AvatarFile> avatarOf(String username) {
        return unitOfWork.execute(() -> users.findByUsername(username)
                        .filter(User::canAuthenticate)
                        .filter(user -> !user.profileHidden())
                        .flatMap(User::avatarImageId))
                .flatMap(avatars::open);
    }

    private User load(UUID userId) {
        return users.findById(new UserId(userId)).orElseThrow(() -> new ResourceNotFoundException("Usuário"));
    }

    private record Holder(MyProfile profile, Optional<UUID> previous) {
    }

    /**
     * Visível só para o próprio usuário.
     *
     * @param termsAccepted     se a versão aceita é a vigente (sem isso não dá para publicar)
     * @param profileHidden     bio/foto ocultadas do público pela moderação
     * @param hasRecoveryPhrase se a conta já tem frase de recuperação (contas antigas geram no próximo login)
     */
    public record MyProfile(UUID id, String username, String bio, boolean hasAvatar, String role,
                            String termsVersion, Instant termsAcceptedAt, boolean termsAccepted, String currentTermsVersion,
                            boolean profileHidden, boolean hasRecoveryPhrase) {

        static MyProfile of(User user) {
            return new MyProfile(user.id().value(), user.username().value(),
                    user.bio().orElse(null), user.avatarImageId().isPresent(), user.role().name(),
                    user.termsVersion().orElse(null), user.termsAcceptedAt().orElse(null),
                    user.hasAcceptedCurrentTerms(), TermsOfUse.CURRENT_VERSION, user.profileHidden(),
                    user.hasRecovery());
        }
    }
}
