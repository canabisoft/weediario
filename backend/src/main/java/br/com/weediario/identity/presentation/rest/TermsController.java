package br.com.weediario.identity.presentation.rest;

import br.com.weediario.identity.domain.model.TermsOfUse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDate;

/**
 * Versão vigente dos Termos de Uso e a data em que ela foi publicada. A data vem da configuração
 * ({@code WEEDIARIO_TERMS_PUBLISHED_ON}, ISO, ex. 2026-11-01): é o dia do lançamento em produção, não a data em que
 * o texto foi escrito. Sem a data configurada, a página não mostra data. Público.
 */
@RestController
public class TermsController {

    private final LocalDate publishedOn;

    public TermsController(@Value("${weediario.identity.terms-published-on:}") String publishedOn) {
        this.publishedOn = publishedOn == null || publishedOn.isBlank() ? null : LocalDate.parse(publishedOn.strip());
    }

    @GetMapping("/api/v1/terms")
    public ResponseEntity<Terms> current() {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .body(new Terms(TermsOfUse.CURRENT_VERSION, publishedOn));
    }

    public record Terms(String version, LocalDate publishedOn) {
    }
}
