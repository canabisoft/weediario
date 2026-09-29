package br.com.weediario.diary.domain.model;

import br.com.weediario.shared.domain.Guard;

import java.util.Optional;

/**
 * Genética cultivada. O breeder/seedbank é opcional (ex.: semente de bag, clone de amigo).
 */
public record Strain(String name, String breeder) {

    public static final int NAME_MAX_LENGTH = 80;
    public static final int BREEDER_MAX_LENGTH = 80;

    public Strain {
        name = Guard.lengthBetween(Guard.notBlank(name, "Strain"), 1, NAME_MAX_LENGTH, "Strain");
        breeder = breeder == null || breeder.isBlank()
                ? null
                : Guard.lengthBetween(breeder.strip(), 1, BREEDER_MAX_LENGTH, "Breeder/Seedbank");
    }

    public Optional<String> breederName() {
        return Optional.ofNullable(breeder);
    }
}
