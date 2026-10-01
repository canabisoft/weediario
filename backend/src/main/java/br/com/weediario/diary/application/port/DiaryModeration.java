package br.com.weediario.diary.application.port;

import java.util.UUID;

/** Manda para a fila da moderação um diário que o filtro anti-comércio reteve (ele já fica privado e travado). */
public interface DiaryModeration {

    void heldByFilter(UUID diaryId, String reason);
}
