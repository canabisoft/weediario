package br.com.weediario.diary.domain.model;

import java.util.UUID;

/**
 * Produto do catálogo como foi registrado no setup/diário (nome e marca congelados, id para os rankings).
 */
public record ProductSnapshot(UUID id, String name, String brand, String category) {
}
