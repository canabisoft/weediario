package br.com.weediario.diary.domain.model;

import br.com.weediario.shared.domain.DomainException;
import br.com.weediario.shared.domain.Guard;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * Entrada da linha do tempo. É um aggregate separado de {@link GrowDiary} porque um diário acumula
 * centenas de updates e carregá-los todos para cada alteração seria inviável.
 * <p>
 * Registra apenas a data do cultivo (sem hora) e nenhuma informação de onde ou de qual aparelho foi publicado.
 */
public final class DiaryUpdate {

    public static final int NOTE_MAX_LENGTH = 5000;
    public static final int MAX_PHOTOS = 10;

    private final DiaryUpdateId id;
    private final GrowDiaryId diaryId;
    private final GrowthStage stage;
    private final LocalDate recordedOn;
    private final String note;
    private final GrowMetrics metrics;
    private final List<UUID> photoIds;
    private final Instant createdAt;

    private DiaryUpdate(DiaryUpdateId id, GrowDiaryId diaryId, GrowthStage stage, LocalDate recordedOn, String note,
                        GrowMetrics metrics, List<UUID> photoIds, Instant createdAt) {
        this.id = Guard.notNull(id, "Id");
        this.diaryId = Guard.notNull(diaryId, "Diário");
        this.stage = Guard.notNull(stage, "Estágio");
        this.recordedOn = Guard.notNull(recordedOn, "Data");
        this.note = normalizeNote(note);
        this.metrics = metrics == null ? GrowMetrics.EMPTY : metrics;
        this.photoIds = List.copyOf(Guard.notNull(photoIds, "Fotos"));
        this.createdAt = Guard.notNull(createdAt, "Data de criação");
    }

    /**
     * Publica um update e avança o progresso do diário. As regras que dependem do diário
     * (autor, status, intervalo de datas) são verificadas por ele em {@link GrowDiary#recordUpdate}.
     */
    public static DiaryUpdate publish(GrowDiary diary, GrowerId author, GrowthStage stage, LocalDate recordedOn,
                                      String note, GrowMetrics metrics, List<UUID> photoIds, Clock clock) {
        List<UUID> photos = photoIds == null ? List.of() : List.copyOf(new LinkedHashSet<>(photoIds));
        if (photos.size() > MAX_PHOTOS) {
            throw new DomainException("Máximo de " + MAX_PHOTOS + " fotos por update");
        }
        boolean hasNote = note != null && !note.isBlank();
        boolean hasMetrics = metrics != null && !metrics.isEmpty();
        if (!hasNote && !hasMetrics && photos.isEmpty()) {
            throw new DomainException("O update precisa de uma foto, uma anotação ou alguma métrica");
        }
        diary.recordUpdate(author, stage, recordedOn, clock);
        return new DiaryUpdate(DiaryUpdateId.newId(), diary.id(), stage, recordedOn, note, metrics, photos, clock.instant());
    }

    public static DiaryUpdate restore(DiaryUpdateId id, GrowDiaryId diaryId, GrowthStage stage, LocalDate recordedOn,
                                      String note, GrowMetrics metrics, List<UUID> photoIds, Instant createdAt) {
        return new DiaryUpdate(id, diaryId, stage, recordedOn, note, metrics, photoIds, createdAt);
    }

    public boolean belongsTo(GrowDiaryId diary) {
        return diaryId.equals(diary);
    }

    /**
     * Texto livre guardado como veio (sem HTML-encode no backend): o Angular faz o escape na renderização.
     * Removemos só caracteres de controle invisíveis, que não têm uso legítimo aqui.
     */
    private static String normalizeNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        String cleaned = note.replaceAll("[\\p{Cntrl}&&[^\n\t]]", "").strip();
        if (cleaned.length() > NOTE_MAX_LENGTH) {
            throw new DomainException("A anotação deve ter no máximo " + NOTE_MAX_LENGTH + " caracteres");
        }
        return cleaned;
    }

    public DiaryUpdateId id() {
        return id;
    }

    public GrowDiaryId diaryId() {
        return diaryId;
    }

    public GrowthStage stage() {
        return stage;
    }

    public LocalDate recordedOn() {
        return recordedOn;
    }

    public String note() {
        return note;
    }

    public GrowMetrics metrics() {
        return metrics;
    }

    public List<UUID> photoIds() {
        return photoIds;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
