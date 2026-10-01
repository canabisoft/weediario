package br.com.weediario.treatment.domain.model;

import br.com.weediario.shared.domain.DomainException;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Consentimento específico para tratar dados de saúde (LGPD, art. 11, I), separado do cadastro.
 * Cada conta consente por si, inclusive cuidadores.
 */
public record TreatmentConsent(UUID userId, String termsVersion, Instant acceptedAt) {

    /** Muda quando o texto do termo muda: quem aceitou uma versão antiga precisa aceitar de novo. */
    public static final String CURRENT_TERMS = "2026-09";

    public static TreatmentConsent accept(UUID userId, String termsVersion, Clock clock) {
        if (!CURRENT_TERMS.equals(termsVersion)) {
            throw new DomainException("Leia e aceite a versão atual do termo");
        }
        return new TreatmentConsent(userId, termsVersion, clock.instant());
    }

    public boolean current() {
        return CURRENT_TERMS.equals(termsVersion);
    }
}
