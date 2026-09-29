package br.com.weediario.identity.application.port;

import java.util.Optional;
import java.util.UUID;

/**
 * Fotos de perfil, guardadas pelo contexto de mídia (já sanitizadas, sem EXIF).
 */
public interface AvatarImages {

    /** Vincula uma imagem enviada pelo próprio usuário. Falha se for de outra pessoa ou já estiver em uso. */
    void attach(UUID ownerId, UUID imageId);

    void discard(UUID imageId);

    Optional<byte[]> open(UUID imageId);
}
