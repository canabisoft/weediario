package br.com.weediario.treatment.domain.model;

import br.com.weediario.shared.domain.Guard;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Perfil de quem faz o tratamento. Pode ser a própria pessoa da conta ou um dependente (filho, familiar) que a
 * conta acompanha como cuidadora. O nome é só um apelido de identificação; não pedimos nome civil nem documento.
 *
 * @param ownerId conta responsável pelo perfil (cria, apaga, convida cuidadores)
 */
public record Patient(UUID id, UUID ownerId, String name, String notes, Instant createdAt) {

    public static final int NAME_MAX_LENGTH = 60;
    public static final int NOTES_MAX_LENGTH = 1000;
    public static final int MAX_PER_OWNER = 10;
    public static final int MAX_CAREGIVERS = 5;

    public Patient {
        Guard.notNull(id, "Id");
        Guard.notNull(ownerId, "Responsável");
        name = Guard.lengthBetween(Guard.notBlank(name, "Nome").strip(), 1, NAME_MAX_LENGTH, "Nome");
        notes = MedicineBatch.optionalText(notes, NOTES_MAX_LENGTH, "Observações");
        Guard.notNull(createdAt, "Data de criação");
    }

    public static Patient create(UUID ownerId, String name, String notes, Clock clock) {
        return new Patient(UUID.randomUUID(), ownerId, name, notes, clock.instant());
    }

    public Patient rename(String newName, String newNotes) {
        return new Patient(id, ownerId, newName, newNotes, createdAt);
    }
}
