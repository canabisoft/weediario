package br.com.weediario.diary.domain.model;

import br.com.weediario.shared.domain.Guard;

import java.util.Optional;
import java.util.UUID;

/**
 * Genética cultivada. O breeder/seedbank é opcional (ex.: semente de bag, clone de amigo).
 * <p>
 * O nome fica registrado como estava no momento do cultivo; {@code catalogId}/{@code breederId} ligam o diário
 * ao catálogo da comunidade (base das estatísticas e rankings).
 */
public record Strain(String name, String breeder, UUID catalogId, UUID breederId) {

    public static final int NAME_MAX_LENGTH = 80;
    public static final int BREEDER_MAX_LENGTH = 80;

    public Strain {
        name = Guard.lengthBetween(Guard.notBlank(name, "Strain"), 1, NAME_MAX_LENGTH, "Strain");
        breeder = breeder == null || breeder.isBlank()
                ? null
                : Guard.lengthBetween(breeder.strip(), 1, BREEDER_MAX_LENGTH, "Breeder/Seedbank");
    }

    /** Strain sem vínculo com o catálogo (usada em testes e dados antigos). */
    public Strain(String name, String breeder) {
        this(name, breeder, null, null);
    }

    public Optional<String> breederName() {
        return Optional.ofNullable(breeder);
    }
}
