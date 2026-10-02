package br.com.weediario.treatment.infrastructure.persistence;

import br.com.weediario.treatment.domain.model.Dose;
import br.com.weediario.treatment.domain.model.MedicineBatch;
import br.com.weediario.treatment.domain.model.MedicineBatch.DoseMeasure;
import br.com.weediario.treatment.domain.model.MedicineForm;
import br.com.weediario.treatment.domain.model.Patient;
import br.com.weediario.treatment.domain.model.SideEffectEntry;
import br.com.weediario.treatment.domain.model.StockAdjustment;
import br.com.weediario.treatment.domain.model.Symptom;
import br.com.weediario.treatment.domain.model.SymptomRating;
import br.com.weediario.treatment.domain.model.TreatmentConsent;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.AccessRole;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.DoseUnit;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.Intensity;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.Origin;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.Route;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.SideEffect;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.SymptomKind;
import br.com.weediario.treatment.domain.repository.TreatmentRepository;
import br.com.weediario.treatment.infrastructure.security.FieldCipher;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcTreatmentRepository implements TreatmentRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final FieldCipher cipher;

    public JdbcTreatmentRepository(NamedParameterJdbcTemplate jdbc, FieldCipher cipher) {
        this.jdbc = jdbc;
        this.cipher = cipher;
    }

    // ------------------------------------------------------------------------------- consentimento

    @Override
    public Optional<TreatmentConsent> consentOf(UUID userId) {
        return first(jdbc.query("select * from treatment_consents where user_id = :user", params("user", userId),
                (rs, n) -> new TreatmentConsent(rs.getObject("user_id", UUID.class), rs.getString("terms_version"),
                        rs.getTimestamp("accepted_at").toInstant())));
    }

    @Override
    public void saveConsent(TreatmentConsent consent) {
        jdbc.update("""
                insert into treatment_consents (user_id, terms_version, accepted_at) values (:user, :version, :at)
                on conflict (user_id) do update set terms_version = excluded.terms_version, accepted_at = excluded.accepted_at""",
                params("user", consent.userId()).addValue("version", consent.termsVersion())
                        .addValue("at", ts(consent.acceptedAt())));
    }

    @Override
    public void deleteConsent(UUID userId) {
        jdbc.update("delete from treatment_consents where user_id = :user", params("user", userId));
    }

    // ---------------------------------------------------------------------------- pacientes e acesso

    @Override
    public Patient savePatient(Patient patient) {
        jdbc.update("""
                insert into treatment_patients (id, owner_id, name_enc, notes_enc, created_at)
                values (:id, :owner, :name, :notes, :at)
                on conflict (id) do update set name_enc = excluded.name_enc, notes_enc = excluded.notes_enc""",
                params("id", patient.id()).addValue("owner", patient.ownerId())
                        .addValue("name", cipher.encrypt(patient.name(), ctx("treatment_patients", "name_enc", patient.id())))
                        .addValue("notes", cipher.encrypt(patient.notes(), ctx("treatment_patients", "notes_enc", patient.id())),
                                Types.BINARY)
                        .addValue("at", ts(patient.createdAt())));
        return patient;
    }

    @Override
    public Optional<Patient> findPatient(UUID id) {
        return first(jdbc.query("select * from treatment_patients where id = :id", params("id", id),
                (rs, n) -> new Patient(rs.getObject("id", UUID.class), rs.getObject("owner_id", UUID.class),
                        dec(rs, "treatment_patients", "name_enc", "id"), dec(rs, "treatment_patients", "notes_enc", "id"),
                        rs.getTimestamp("created_at").toInstant())));
    }

    @Override
    public long countPatientsOwnedBy(UUID ownerId) {
        return count("select count(*) from treatment_patients where owner_id = :id", ownerId);
    }

    @Override
    public void deletePatient(UUID id) {
        jdbc.update("delete from treatment_patients where id = :id", params("id", id));
    }

    @Override
    public void deletePatientsOwnedBy(UUID ownerId) {
        jdbc.update("delete from treatment_patients where owner_id = :id", params("id", ownerId));
    }

    @Override
    public Optional<AccessRole> accessOf(UUID patientId, UUID userId) {
        return first(jdbc.query("""
                select case when p.owner_id = :user then 'OWNER' else 'CAREGIVER' end as role
                  from treatment_patients p
                 where p.id = :patient
                   and (p.owner_id = :user
                        or exists (select 1 from treatment_caregivers c
                                    where c.patient_id = p.id and c.user_id = :user and c.accepted))""",
                params("patient", patientId).addValue("user", userId),
                (rs, n) -> AccessRole.valueOf(rs.getString("role"))));
    }

    @Override
    public void invite(UUID patientId, UUID userId, Instant at) {
        jdbc.update("""
                insert into treatment_caregivers (patient_id, user_id, accepted, invited_at)
                values (:patient, :user, false, :at)
                on conflict (patient_id, user_id) do nothing""",
                params("patient", patientId).addValue("user", userId).addValue("at", ts(at)));
    }

    @Override
    public long countCaregivers(UUID patientId) {
        return count("select count(*) from treatment_caregivers where patient_id = :id", patientId);
    }

    @Override
    public boolean acceptInvite(UUID patientId, UUID userId, Instant at) {
        return jdbc.update("""
                update treatment_caregivers set accepted = true, accepted_at = :at
                 where patient_id = :patient and user_id = :user and not accepted""",
                params("patient", patientId).addValue("user", userId).addValue("at", ts(at))) > 0;
    }

    @Override
    public boolean removeCaregiver(UUID patientId, UUID userId) {
        return jdbc.update("delete from treatment_caregivers where patient_id = :patient and user_id = :user",
                params("patient", patientId).addValue("user", userId)) > 0;
    }

    @Override
    public void leaveAllCaregiving(UUID userId) {
        jdbc.update("delete from treatment_caregivers where user_id = :user", params("user", userId));
    }

    // ------------------------------------------------------------------------------------ sintomas

    @Override
    public Symptom saveSymptom(Symptom symptom) {
        jdbc.update("""
                insert into treatment_symptoms (id, patient_id, name_enc, kind, archived, created_at)
                values (:id, :patient, :name, :kind, :archived, :at)
                on conflict (id) do update set archived = excluded.archived""",
                params("id", symptom.id()).addValue("patient", symptom.patientId())
                        .addValue("name", cipher.encrypt(symptom.name(), ctx("treatment_symptoms", "name_enc", symptom.id())))
                        .addValue("kind", symptom.kind().name())
                        .addValue("archived", symptom.archived()).addValue("at", ts(symptom.createdAt())));
        return symptom;
    }

    @Override
    public Optional<Symptom> findSymptom(UUID id) {
        return first(jdbc.query("select * from treatment_symptoms where id = :id", params("id", id),
                (rs, n) -> new Symptom(rs.getObject("id", UUID.class), rs.getObject("patient_id", UUID.class),
                        dec(rs, "treatment_symptoms", "name_enc", "id"), SymptomKind.valueOf(rs.getString("kind")),
                        rs.getBoolean("archived"), rs.getTimestamp("created_at").toInstant())));
    }

    @Override
    public long countActiveSymptoms(UUID patientId) {
        return count("select count(*) from treatment_symptoms where patient_id = :id and not archived", patientId);
    }

    // ------------------------------------------------------------------------------ remédios e estoque

    @Override
    public MedicineBatch saveBatch(MedicineBatch batch) {
        jdbc.update("""
                insert into treatment_batches (id, patient_id, name_enc, form, origin, thc, cbd, drops_per_ml,
                                               initial_quantity, manufactured_on, expires_on, notes_enc, archived, created_at)
                values (:id, :patient, :name, :form, :origin, :thc, :cbd, :drops, :initial, :manufactured, :expires,
                        :notes, :archived, :at)
                on conflict (id) do update set
                    name_enc = excluded.name_enc, origin = excluded.origin, thc = excluded.thc, cbd = excluded.cbd,
                    drops_per_ml = excluded.drops_per_ml, manufactured_on = excluded.manufactured_on,
                    expires_on = excluded.expires_on, notes_enc = excluded.notes_enc, archived = excluded.archived""",
                params("id", batch.id()).addValue("patient", batch.patientId())
                        .addValue("name", cipher.encrypt(batch.name(), ctx("treatment_batches", "name_enc", batch.id())))
                        .addValue("form", batch.form().name()).addValue("origin", batch.origin().name())
                        .addValue("thc", batch.thc()).addValue("cbd", batch.cbd())
                        .addValue("drops", batch.dropsPerMl()).addValue("initial", batch.initialQuantity())
                        .addValue("manufactured", date(batch.manufacturedOn()), Types.DATE)
                        .addValue("expires", date(batch.expiresOn()), Types.DATE)
                        .addValue("notes", cipher.encrypt(batch.notes(), ctx("treatment_batches", "notes_enc", batch.id())),
                                Types.BINARY)
                        .addValue("archived", batch.archived()).addValue("at", ts(batch.createdAt())));
        return batch;
    }

    @Override
    public Optional<MedicineBatch> findBatch(UUID id) {
        return first(jdbc.query("select * from treatment_batches where id = :id", params("id", id),
                (rs, n) -> new MedicineBatch(rs.getObject("id", UUID.class), rs.getObject("patient_id", UUID.class),
                        dec(rs, "treatment_batches", "name_enc", "id"), MedicineForm.valueOf(rs.getString("form")),
                        Origin.valueOf(rs.getString("origin")), rs.getBigDecimal("thc"), rs.getBigDecimal("cbd"),
                        rs.getBigDecimal("drops_per_ml"), rs.getBigDecimal("initial_quantity"),
                        localDate(rs.getDate("manufactured_on")), localDate(rs.getDate("expires_on")),
                        dec(rs, "treatment_batches", "notes_enc", "id"), rs.getBoolean("archived"),
                        rs.getTimestamp("created_at").toInstant())));
    }

    @Override
    public void saveAdjustment(StockAdjustment adjustment) {
        jdbc.update("""
                insert into treatment_stock_adjustments (id, batch_id, kind, quantity, occurred_at, recorded_by)
                values (:id, :batch, :kind, :quantity, :at, :by)""",
                params("id", adjustment.id()).addValue("batch", adjustment.batchId())
                        .addValue("kind", adjustment.kind().name()).addValue("quantity", adjustment.quantity())
                        .addValue("at", ts(adjustment.occurredAt())).addValue("by", adjustment.recordedBy(), Types.OTHER));
    }

    // -------------------------------------------------------------------------------------- registros

    @Override
    public Dose saveDose(Dose dose) {
        jdbc.update("""
                insert into treatment_doses (id, patient_id, batch_id, amount, unit, route, taken_at, stock_quantity,
                                             thc_mg, cbd_mg, notes_enc, recorded_by, created_at)
                values (:id, :patient, :batch, :amount, :unit, :route, :takenAt, :stock, :thc, :cbd, :notes, :by, :at)""",
                params("id", dose.id()).addValue("patient", dose.patientId()).addValue("batch", dose.batchId())
                        .addValue("amount", dose.amount()).addValue("unit", dose.unit().name())
                        .addValue("route", dose.route().name()).addValue("takenAt", ts(dose.takenAt()))
                        .addValue("stock", dose.measure().stockQuantity(), Types.NUMERIC)
                        .addValue("thc", dose.measure().thcMg(), Types.NUMERIC)
                        .addValue("cbd", dose.measure().cbdMg(), Types.NUMERIC)
                        .addValue("notes", cipher.encrypt(dose.notes(), ctx("treatment_doses", "notes_enc", dose.id())),
                                Types.BINARY)
                        .addValue("by", dose.recordedBy(), Types.OTHER).addValue("at", ts(dose.createdAt())));
        return dose;
    }

    @Override
    public Optional<Dose> findDose(UUID id) {
        return first(jdbc.query("select * from treatment_doses where id = :id", params("id", id),
                (rs, n) -> new Dose(rs.getObject("id", UUID.class), rs.getObject("patient_id", UUID.class),
                        rs.getObject("batch_id", UUID.class), rs.getBigDecimal("amount"),
                        DoseUnit.valueOf(rs.getString("unit")), Route.valueOf(rs.getString("route")),
                        rs.getTimestamp("taken_at").toInstant(),
                        new DoseMeasure(rs.getBigDecimal("stock_quantity"), rs.getBigDecimal("thc_mg"),
                                rs.getBigDecimal("cbd_mg")),
                        dec(rs, "treatment_doses", "notes_enc", "id"), rs.getObject("recorded_by", UUID.class),
                        rs.getTimestamp("created_at").toInstant())));
    }

    @Override
    public void deleteDose(UUID id) {
        jdbc.update("delete from treatment_doses where id = :id", params("id", id));
    }

    @Override
    public void saveRating(SymptomRating rating) {
        MapSqlParameterSource params = params("id", rating.id()).addValue("patient", rating.patientId())
                .addValue("symptom", rating.symptomId()).addValue("dose", rating.doseId(), Types.OTHER)
                .addValue("moment", rating.moment().name()).addValue("value", rating.value())
                .addValue("at", ts(rating.recordedAt())).addValue("by", rating.recordedBy(), Types.OTHER);
        String insert = """
                insert into treatment_symptom_ratings (id, patient_id, symptom_id, dose_id, moment, value, recorded_at,
                                                       recorded_by)
                values (:id, :patient, :symptom, :dose, :moment, :value, :at, :by)""";
        jdbc.update(rating.doseId() == null ? insert : insert + """

                on conflict (dose_id, symptom_id, moment) where dose_id is not null
                do update set value = excluded.value, recorded_at = excluded.recorded_at, recorded_by = excluded.recorded_by""",
                params);
    }

    @Override
    public void deleteRating(UUID id, UUID patientId) {
        jdbc.update("delete from treatment_symptom_ratings where id = :id and patient_id = :patient",
                params("id", id).addValue("patient", patientId));
    }

    @Override
    public void saveSideEffect(SideEffectEntry entry) {
        jdbc.update("""
                insert into treatment_side_effects (id, patient_id, dose_id, effect, intensity, recorded_at, recorded_by)
                values (:id, :patient, :dose, :effect, :intensity, :at, :by)""",
                params("id", entry.id()).addValue("patient", entry.patientId())
                        .addValue("dose", entry.doseId(), Types.OTHER).addValue("effect", entry.effect().name())
                        .addValue("intensity", entry.intensity().name()).addValue("at", ts(entry.recordedAt()))
                        .addValue("by", entry.recordedBy(), Types.OTHER));
    }

    @Override
    public Optional<SideEffectEntry> findSideEffect(UUID id) {
        return first(jdbc.query("select * from treatment_side_effects where id = :id", params("id", id),
                (rs, n) -> new SideEffectEntry(rs.getObject("id", UUID.class), rs.getObject("patient_id", UUID.class),
                        rs.getObject("dose_id", UUID.class), SideEffect.valueOf(rs.getString("effect")),
                        Intensity.valueOf(rs.getString("intensity")), rs.getTimestamp("recorded_at").toInstant(),
                        rs.getObject("recorded_by", UUID.class))));
    }

    @Override
    public void deleteSideEffect(UUID id) {
        jdbc.update("delete from treatment_side_effects where id = :id", params("id", id));
    }

    // ------------------------------------------------------------------------------------- auxiliares

    private long count(String sql, UUID id) {
        Long count = jdbc.queryForObject(sql, params("id", id), Long.class);
        return count == null ? 0 : count;
    }

    private static MapSqlParameterSource params(String name, Object value) {
        return new MapSqlParameterSource(name, value);
    }

    private static Timestamp ts(Instant instant) {
        return Timestamp.from(instant);
    }

    private static Date date(LocalDate date) {
        return date == null ? null : Date.valueOf(date);
    }

    private static LocalDate localDate(Date date) {
        return date == null ? null : date.toLocalDate();
    }

    private static <T> Optional<T> first(List<T> list) {
        return list.stream().findFirst();
    }

    private static String ctx(String table, String column, UUID id) {
        return FieldCipher.context(table, column, id);
    }

    /** Decifra a coluna amarrada à linha (id na coluna {@code idColumn}). */
    private String dec(java.sql.ResultSet rs, String table, String column, String idColumn) throws java.sql.SQLException {
        return cipher.decrypt(rs.getBytes(column), ctx(table, column, rs.getObject(idColumn, UUID.class)));
    }
}
