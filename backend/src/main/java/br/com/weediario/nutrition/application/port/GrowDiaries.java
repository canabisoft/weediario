package br.com.weediario.nutrition.application.port;

import java.util.Optional;
import java.util.UUID;

/**
 * Consulta ao contexto de diários: só o necessário para vincular receitas a um cultivo.
 */
public interface GrowDiaries {

    Optional<DiaryRef> find(UUID diaryId);

    /** {@code mineralLike}: meio mineral ou inerte, em que a nutrição vem de sais (fertirrigação). */
    record DiaryRef(UUID id, UUID ownerId, boolean isPublic, boolean mineralLike) {
    }
}
