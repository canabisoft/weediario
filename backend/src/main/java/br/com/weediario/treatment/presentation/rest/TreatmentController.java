package br.com.weediario.treatment.presentation.rest;

import br.com.weediario.shared.presentation.Viewer;
import br.com.weediario.treatment.application.query.TreatmentQueries.CaregiverView;
import br.com.weediario.treatment.application.query.TreatmentQueries.SymptomView;
import br.com.weediario.treatment.application.usecase.MedicinesUseCase;
import br.com.weediario.treatment.application.usecase.MedicinesUseCase.BatchCard;
import br.com.weediario.treatment.application.usecase.PatientsUseCase;
import br.com.weediario.treatment.application.usecase.TreatmentConsentUseCase;
import br.com.weediario.treatment.application.usecase.TreatmentConsentUseCase.ConsentStatus;
import br.com.weediario.treatment.application.usecase.TreatmentExportUseCase;
import br.com.weediario.treatment.application.usecase.TreatmentExportUseCase.TreatmentExport;
import br.com.weediario.treatment.application.usecase.TreatmentLogUseCase;
import br.com.weediario.treatment.application.usecase.TreatmentLogUseCase.DoseCommand;
import br.com.weediario.treatment.application.usecase.TreatmentLogUseCase.RatingInput;
import br.com.weediario.treatment.application.usecase.TreatmentLogUseCase.Series;
import br.com.weediario.treatment.application.usecase.TreatmentLogUseCase.SideEffectInput;
import br.com.weediario.treatment.application.usecase.TreatmentLogUseCase.Timeline;
import br.com.weediario.treatment.application.usecase.TreatmentLogUseCase.Today;
import br.com.weediario.treatment.application.usecase.TreatmentReportUseCase;
import br.com.weediario.treatment.domain.model.Dose;
import br.com.weediario.treatment.domain.model.MedicineBatch;
import br.com.weediario.treatment.domain.model.MedicineForm;
import br.com.weediario.treatment.domain.model.Patient;
import br.com.weediario.treatment.domain.model.Symptom;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.DoseUnit;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.Intensity;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.Origin;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.Route;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.SideEffect;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.StockAdjustmentKind;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.SymptomKind;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Acompanhamento de tratamento. Tudo fica sob {@code /api/v1/me}: só a própria conta (ou cuidadores que o
 * responsável convidou e que aceitaram) enxerga. Nenhuma rota é pública e as respostas não vão para cache.
 */
@RestController
@RequestMapping("/api/v1/me/treatment")
public class TreatmentController {

    private final TreatmentConsentUseCase consent;
    private final PatientsUseCase patients;
    private final MedicinesUseCase medicines;
    private final TreatmentLogUseCase log;
    private final TreatmentReportUseCase report;
    private final TreatmentExportUseCase export;

    public TreatmentController(TreatmentConsentUseCase consent, PatientsUseCase patients, MedicinesUseCase medicines,
                               TreatmentLogUseCase log, TreatmentReportUseCase report, TreatmentExportUseCase export) {
        this.consent = consent;
        this.patients = patients;
        this.medicines = medicines;
        this.log = log;
        this.report = report;
        this.export = export;
    }

    // ------------------------------------------------------------------------------- consentimento

    @GetMapping("/consent")
    public ConsentStatus consent(@AuthenticationPrincipal Jwt principal) {
        return consent.status(Viewer.requireId(principal));
    }

    @PutMapping("/consent")
    public ConsentStatus accept(@AuthenticationPrincipal Jwt principal, @Valid @RequestBody ConsentRequest request) {
        return consent.accept(Viewer.requireId(principal), request.termsVersion());
    }

    /** Revoga o consentimento e apaga tudo o que a conta tem no módulo. */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeAndErase(@AuthenticationPrincipal Jwt principal) {
        consent.revokeAndErase(Viewer.requireId(principal));
    }

    @GetMapping("/export")
    public ResponseEntity<TreatmentExport> export(@AuthenticationPrincipal Jwt principal) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("weediario-tratamento.json").build().toString())
                .body(export.export(Viewer.requireId(principal)));
    }

    // ---------------------------------------------------------------------------- pacientes e acesso

    @GetMapping("/patients")
    public PatientsUseCase.Overview patients(@AuthenticationPrincipal Jwt principal) {
        return patients.overview(Viewer.requireId(principal));
    }

    @PostMapping("/patients")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, UUID> createPatient(@AuthenticationPrincipal Jwt principal,
                                           @Valid @RequestBody PatientRequest request) {
        return Map.of("id", patients.create(Viewer.requireId(principal), request.name(), request.notes()));
    }

    @PutMapping("/patients/{patientId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updatePatient(@AuthenticationPrincipal Jwt principal, @PathVariable UUID patientId,
                              @Valid @RequestBody PatientRequest request) {
        patients.update(patientId, Viewer.requireId(principal), request.name(), request.notes());
    }

    @DeleteMapping("/patients/{patientId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePatient(@AuthenticationPrincipal Jwt principal, @PathVariable UUID patientId) {
        patients.delete(patientId, Viewer.requireId(principal));
    }

    @GetMapping("/patients/{patientId}/caregivers")
    public List<CaregiverView> caregivers(@AuthenticationPrincipal Jwt principal, @PathVariable UUID patientId) {
        return patients.caregivers(patientId, Viewer.requireId(principal));
    }

    @PostMapping("/patients/{patientId}/caregivers")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void invite(@AuthenticationPrincipal Jwt principal, @PathVariable UUID patientId,
                       @Valid @RequestBody InviteRequest request) {
        patients.invite(patientId, Viewer.requireId(principal), request.username());
    }

    @DeleteMapping("/patients/{patientId}/caregivers/{username}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeCaregiver(@AuthenticationPrincipal Jwt principal, @PathVariable UUID patientId,
                                @PathVariable @Size(max = 30) String username) {
        patients.removeCaregiver(patientId, Viewer.requireId(principal), username);
    }

    /** Aceitar um convite para ser cuidador. */
    @PostMapping("/invites/{patientId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void acceptInvite(@AuthenticationPrincipal Jwt principal, @PathVariable UUID patientId) {
        patients.acceptInvite(patientId, Viewer.requireId(principal));
    }

    /** Recusar um convite ou deixar de cuidar de um perfil. */
    @DeleteMapping("/invites/{patientId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@AuthenticationPrincipal Jwt principal, @PathVariable UUID patientId) {
        patients.leave(patientId, Viewer.requireId(principal));
    }

    // ------------------------------------------------------------------------------------ sintomas

    @GetMapping("/patients/{patientId}/symptoms")
    public List<SymptomView> symptoms(@AuthenticationPrincipal Jwt principal, @PathVariable UUID patientId,
                                      @RequestParam(defaultValue = "false") boolean archived) {
        return patients.symptoms(patientId, Viewer.requireId(principal), archived);
    }

    @PostMapping("/patients/{patientId}/symptoms")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, UUID> addSymptom(@AuthenticationPrincipal Jwt principal, @PathVariable UUID patientId,
                                        @Valid @RequestBody SymptomRequest request) {
        return Map.of("id", patients.addSymptom(patientId, Viewer.requireId(principal), request.name(), request.kind()));
    }

    @PatchMapping("/patients/{patientId}/symptoms/{symptomId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archiveSymptom(@AuthenticationPrincipal Jwt principal, @PathVariable UUID patientId,
                               @PathVariable UUID symptomId, @RequestBody ArchiveRequest request) {
        patients.archiveSymptom(patientId, symptomId, Viewer.requireId(principal), request.archived());
    }

    // ------------------------------------------------------------------------------ remédios e estoque

    @GetMapping("/patients/{patientId}/medicines")
    public List<BatchCard> medicines(@AuthenticationPrincipal Jwt principal, @PathVariable UUID patientId,
                                     @RequestParam(defaultValue = "false") boolean archived) {
        return medicines.list(patientId, Viewer.requireId(principal), archived);
    }

    @PostMapping("/patients/{patientId}/medicines")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, UUID> registerMedicine(@AuthenticationPrincipal Jwt principal, @PathVariable UUID patientId,
                                              @Valid @RequestBody MedicineRequest request) {
        return Map.of("id", medicines.register(patientId, Viewer.requireId(principal), request.details()));
    }

    @PutMapping("/patients/{patientId}/medicines/{batchId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateMedicine(@AuthenticationPrincipal Jwt principal, @PathVariable UUID patientId,
                               @PathVariable UUID batchId, @Valid @RequestBody MedicineRequest request) {
        medicines.update(patientId, batchId, Viewer.requireId(principal), request.details());
    }

    @PatchMapping("/patients/{patientId}/medicines/{batchId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archiveMedicine(@AuthenticationPrincipal Jwt principal, @PathVariable UUID patientId,
                                @PathVariable UUID batchId, @RequestBody ArchiveRequest request) {
        medicines.archive(patientId, batchId, Viewer.requireId(principal), request.archived());
    }

    @PostMapping("/patients/{patientId}/medicines/{batchId}/adjustments")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void adjustStock(@AuthenticationPrincipal Jwt principal, @PathVariable UUID patientId,
                            @PathVariable UUID batchId, @Valid @RequestBody AdjustmentRequest request) {
        medicines.adjust(patientId, batchId, Viewer.requireId(principal), request.kind(), request.quantity());
    }

    // -------------------------------------------------------------------------------------- registros

    @GetMapping("/patients/{patientId}/today")
    public Today today(@AuthenticationPrincipal Jwt principal, @PathVariable UUID patientId) {
        return log.today(patientId, Viewer.requireId(principal));
    }

    @PostMapping("/patients/{patientId}/doses")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, UUID> recordDose(@AuthenticationPrincipal Jwt principal, @PathVariable UUID patientId,
                                        @Valid @RequestBody DoseRequest request) {
        return Map.of("id", log.recordDose(patientId, Viewer.requireId(principal), request.command()));
    }

    @DeleteMapping("/patients/{patientId}/doses/{doseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDose(@AuthenticationPrincipal Jwt principal, @PathVariable UUID patientId,
                           @PathVariable UUID doseId) {
        log.deleteDose(patientId, doseId, Viewer.requireId(principal));
    }

    @PutMapping("/patients/{patientId}/doses/{doseId}/after")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rateAfter(@AuthenticationPrincipal Jwt principal, @PathVariable UUID patientId,
                          @PathVariable UUID doseId, @Valid @RequestBody RatingsRequest request) {
        log.rateAfter(patientId, doseId, Viewer.requireId(principal), request.inputs());
    }

    @PostMapping("/patients/{patientId}/checkins")
    @ResponseStatus(HttpStatus.CREATED)
    public void checkin(@AuthenticationPrincipal Jwt principal, @PathVariable UUID patientId,
                        @Valid @RequestBody RatingsRequest request) {
        log.checkin(patientId, Viewer.requireId(principal), request.inputs());
    }

    @DeleteMapping("/patients/{patientId}/ratings/{ratingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRating(@AuthenticationPrincipal Jwt principal, @PathVariable UUID patientId,
                             @PathVariable UUID ratingId) {
        log.deleteRating(patientId, ratingId, Viewer.requireId(principal));
    }

    @PostMapping("/patients/{patientId}/side-effects")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, UUID> addSideEffect(@AuthenticationPrincipal Jwt principal, @PathVariable UUID patientId,
                                           @Valid @RequestBody SideEffectRequest request) {
        return Map.of("id", log.addSideEffect(patientId, Viewer.requireId(principal), request.doseId(),
                request.effect(), request.intensity()));
    }

    @DeleteMapping("/patients/{patientId}/side-effects/{entryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSideEffect(@AuthenticationPrincipal Jwt principal, @PathVariable UUID patientId,
                                 @PathVariable UUID entryId) {
        log.deleteSideEffect(patientId, entryId, Viewer.requireId(principal));
    }

    @GetMapping("/patients/{patientId}/timeline")
    public Timeline timeline(@AuthenticationPrincipal Jwt principal, @PathVariable UUID patientId,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return log.timeline(patientId, Viewer.requireId(principal), from, to);
    }

    @GetMapping("/patients/{patientId}/series")
    public Series series(@AuthenticationPrincipal Jwt principal, @PathVariable UUID patientId,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return log.series(patientId, Viewer.requireId(principal), from, to);
    }

    /** Relatório para a consulta, gerado no servidor. */
    @GetMapping("/patients/{patientId}/report")
    public ResponseEntity<byte[]> report(@AuthenticationPrincipal Jwt principal, @PathVariable UUID patientId,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        byte[] pdf = report.pdf(patientId, Viewer.requireId(principal), from, to);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("relatorio-tratamento.pdf").build().toString())
                .body(pdf);
    }

    // ------------------------------------------------------------------------------------------ DTOs

    public record ConsentRequest(@NotBlank @Size(max = 20) String termsVersion) {
    }

    public record PatientRequest(@NotBlank @Size(max = Patient.NAME_MAX_LENGTH) String name,
                                 @Size(max = Patient.NOTES_MAX_LENGTH) String notes) {
    }

    public record InviteRequest(@NotBlank @Size(max = 30) String username) {
    }

    public record SymptomRequest(@NotBlank @Size(max = Symptom.NAME_MAX_LENGTH) String name, SymptomKind kind) {
    }

    public record ArchiveRequest(boolean archived) {
    }

    public record MedicineRequest(@NotBlank @Size(max = MedicineBatch.NAME_MAX_LENGTH) String name,
                                  @NotNull MedicineForm form, Origin origin, BigDecimal thc, BigDecimal cbd,
                                  BigDecimal dropsPerMl, @NotNull BigDecimal initialQuantity, LocalDate manufacturedOn,
                                  LocalDate expiresOn, @Size(max = MedicineBatch.NOTES_MAX_LENGTH) String notes) {

        MedicinesUseCase.Details details() {
            return new MedicinesUseCase.Details(name, form, origin, thc, cbd, dropsPerMl, initialQuantity,
                    manufacturedOn, expiresOn, notes);
        }
    }

    public record AdjustmentRequest(@NotNull StockAdjustmentKind kind, @NotNull BigDecimal quantity) {
    }

    public record RatingRequest(@NotNull UUID symptomId, @Min(0) @Max(Symptom.COUNT_MAX) int value) {

        RatingInput input() {
            return new RatingInput(symptomId, value);
        }
    }

    public record RatingsRequest(@NotNull @Size(min = 1, max = 12) List<@Valid RatingRequest> ratings) {

        List<RatingInput> inputs() {
            return ratings.stream().map(RatingRequest::input).toList();
        }
    }

    public record SideEffectItem(@NotNull SideEffect effect, Intensity intensity) {
    }

    public record SideEffectRequest(UUID doseId, @NotNull SideEffect effect, Intensity intensity) {
    }

    public record DoseRequest(@NotNull UUID batchId, @NotNull BigDecimal amount, @NotNull DoseUnit unit, Route route,
                              Instant takenAt, @Size(max = Dose.NOTES_MAX_LENGTH) String notes,
                              @Size(max = 12) List<@Valid RatingRequest> before,
                              @Size(max = 10) List<@Valid SideEffectItem> sideEffects) {

        DoseCommand command() {
            return new DoseCommand(batchId, amount, unit, route, takenAt, notes,
                    before == null ? List.of() : before.stream().map(RatingRequest::input).toList(),
                    sideEffects == null ? List.of()
                            : sideEffects.stream().map(e -> new SideEffectInput(e.effect(), e.intensity())).toList());
        }
    }
}
