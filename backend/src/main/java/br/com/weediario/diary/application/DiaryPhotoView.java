package br.com.weediario.diary.application;

import java.util.UUID;

/**
 * Foto de um update com as duas URLs prontas: o front mostra {@code thumbnailUrl} em cards e listas e só busca
 * {@code fullUrl} quando o grower abre a foto para inspecionar.
 * <p>
 * As URLs passam pelo endpoint do diário (e não pelo de mídia) porque é a visibilidade do diário que decide quem
 * pode ver a foto. São relativas, para funcionar atrás de qualquer domínio/proxy.
 */
public record DiaryPhotoView(UUID id, String thumbnailUrl, String fullUrl) {

    public static DiaryPhotoView of(UUID diaryId, UUID photoId) {
        return new DiaryPhotoView(photoId, thumbnailUrl(diaryId, photoId), url(diaryId, photoId, "FULL"));
    }

    public static String thumbnailUrl(UUID diaryId, UUID photoId) {
        return url(diaryId, photoId, "THUMBNAIL");
    }

    private static String url(UUID diaryId, UUID photoId, String size) {
        return "/api/v1/diaries/" + diaryId + "/photos/" + photoId + "?size=" + size;
    }
}
