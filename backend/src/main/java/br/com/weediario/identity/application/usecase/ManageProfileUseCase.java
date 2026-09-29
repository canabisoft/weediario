package br.com.weediario.identity.application.usecase;

import br.com.weediario.identity.application.port.AvatarImages;
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
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public ManageProfileUseCase(UserRepository users, AvatarImages avatars, UnitOfWork unitOfWork, Clock clock) {
        this.users = users;
        this.avatars = avatars;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    public MyProfile me(UUID userId) {
        return unitOfWork.execute(() -> MyProfile.of(load(userId)));
    }

    public MyProfile updateBio(UUID userId, String bio) {
        return unitOfWork.execute(() -> {
            User user = load(userId);
            user.updateBio(bio, clock);
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

    public Optional<byte[]> avatarOf(String username) {
        return unitOfWork.execute(() -> users.findByUsername(username)
                        .filter(User::canAuthenticate)
                        .flatMap(User::avatarImageId))
                .flatMap(avatars::open);
    }

    private User load(UUID userId) {
        return users.findById(new UserId(userId)).orElseThrow(() -> new ResourceNotFoundException("Usuário"));
    }

    private record Holder(MyProfile profile, Optional<UUID> previous) {
    }

    /** Visível só para o próprio usuário (inclui o e-mail). */
    public record MyProfile(UUID id, String username, String email, String bio, boolean hasAvatar, String role) {

        static MyProfile of(User user) {
            return new MyProfile(user.id().value(), user.username().value(), user.email().value(),
                    user.bio().orElse(null), user.avatarImageId().isPresent(), user.role().name());
        }
    }
}
