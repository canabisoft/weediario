package br.com.weediario.diary.application.port;

import java.util.UUID;

/** Avisa quem segue o grower de que saiu update novo (só vale para diário público). */
public interface UpdateNotifier {

    void updatePublished(UUID diaryId, UUID ownerId, boolean publicDiary);
}
