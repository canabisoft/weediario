package br.com.weediario.treatment.infrastructure.query;

import br.com.weediario.treatment.application.query.TreatmentQueries;
import br.com.weediario.treatment.domain.model.MedicineForm;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.AccessRole;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.DoseUnit;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.Intensity;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.Origin;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.RatingMoment;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.Route;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.SideEffect;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.SymptomKind;
import br.com.weediario.treatment.infrastructure.security.FieldCipher;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Consultas do acompanhamento. Cada chamada decifra um mesmo texto (nome do remédio, do sintoma) uma vez só.
 */
@Component
public class JdbcTreatmentQueries implements TreatmentQueries {

    /** Doses do paciente no período (usada como subconsulta para notas e efeitos). */
    private static final String DOSES_IN_PERIOD = """
            select id from treatment_doses where patient_id = :patient and taken_at >= :from and taken_at < :to""";

    private final NamedParameterJdbcTemplate jdbc;
    private final FieldCipher cipher;

    public JdbcTreatmentQueries(NamedParameterJdbcTemplate jdbc, FieldCipher cipher) {
        this.jdbc = jdbc;
        this.cipher = cipher;
    }

    @Override
    public List<PatientSummary> patientsOf(UUID userId) {
        return jdbc.query("""
                select p.id, p.name_enc, p.notes_enc, o.username as owner,
                       case when p.owner_id = :user then 'OWNER' else 'CAREGIVER' end as role,
                       (select count(*) from treatment_caregivers c where c.patient_id = p.id and c.accepted) as caregivers
                  from treatment_patients p
                  join users o on o.id = p.owner_id
                 where p.owner_id = :user
                    or exists (select 1 from treatment_caregivers c
                                where c.patient_id = p.id and c.user_id = :user and c.accepted)
                 order by (p.owner_id = :user) desc, p.created_at""",
                new MapSqlParameterSource("user", userId),
                (rs, n) -> new PatientSummary(rs.getObject("id", UUID.class), cipher.decrypt(rs.getBytes("name_enc")),
                        cipher.decrypt(rs.getBytes("notes_enc")), AccessRole.valueOf(rs.getString("role")),
                        rs.getString("owner"), rs.getLong("caregivers")));
    }

    @Override
    public List<InviteView> pendingInvites(UUID userId) {
        return jdbc.query("""
                select p.id, p.name_enc, o.username as owner, c.invited_at
                  from treatment_caregivers c
                  join treatment_patients p on p.id = c.patient_id
                  join users o on o.id = p.owner_id and o.status = 'ACTIVE'
                 where c.user_id = :user and not c.accepted
                 order by c.invited_at desc""",
                new MapSqlParameterSource("user", userId),
                (rs, n) -> new InviteView(rs.getObject("id", UUID.class), cipher.decrypt(rs.getBytes("name_enc")),
                        rs.getString("owner"), instant(rs.getTimestamp("invited_at"))));
    }

    @Override
    public List<CaregiverView> caregivers(UUID patientId) {
        return jdbc.query("""
                select u.username, c.accepted, c.invited_at
                  from treatment_caregivers c join users u on u.id = c.user_id
                 where c.patient_id = :patient
                 order by c.invited_at""",
                new MapSqlParameterSource("patient", patientId),
                (rs, n) -> new CaregiverView(rs.getString("username"), rs.getBoolean("accepted"),
                        instant(rs.getTimestamp("invited_at"))));
    }

    @Override
    public List<SymptomView> symptoms(UUID patientId, boolean includeArchived) {
        return jdbc.query("select * from treatment_symptoms where patient_id = :patient"
                        + (includeArchived ? "" : " and not archived") + " order by archived, created_at",
                new MapSqlParameterSource("patient", patientId),
                (rs, n) -> new SymptomView(rs.getObject("id", UUID.class), cipher.decrypt(rs.getBytes("name_enc")),
                        SymptomKind.valueOf(rs.getString("kind")), rs.getBoolean("archived")));
    }

    @Override
    public List<BatchStock> batches(UUID patientId, boolean includeArchived, Instant usageSince) {
        return jdbc.query("""
                select b.*,
                       b.initial_quantity
                         - coalesce((select sum(d.stock_quantity) from treatment_doses d where d.batch_id = b.id), 0)
                         + coalesce((select sum(a.quantity) from treatment_stock_adjustments a where a.batch_id = b.id), 0)
                           as remaining,
                       coalesce((select sum(d.stock_quantity) from treatment_doses d
                                  where d.batch_id = b.id and d.taken_at >= :since), 0) as used_recently
                  from treatment_batches b
                 where b.patient_id = :patient""" + (includeArchived ? "" : " and not b.archived")
                        + " order by b.archived, b.created_at desc",
                new MapSqlParameterSource("patient", patientId).addValue("since", Timestamp.from(usageSince)),
                (rs, n) -> new BatchStock(rs.getObject("id", UUID.class), cipher.decrypt(rs.getBytes("name_enc")),
                        MedicineForm.valueOf(rs.getString("form")), Origin.valueOf(rs.getString("origin")),
                        rs.getBigDecimal("thc"), rs.getBigDecimal("cbd"), rs.getBigDecimal("drops_per_ml"),
                        rs.getBigDecimal("initial_quantity"), rs.getBigDecimal("remaining"),
                        rs.getBigDecimal("used_recently"), localDate(rs.getDate("manufactured_on")),
                        localDate(rs.getDate("expires_on")), cipher.decrypt(rs.getBytes("notes_enc")),
                        rs.getBoolean("archived")));
    }

    @Override
    public List<DoseEntry> doses(UUID patientId, Instant from, Instant to) {
        MapSqlParameterSource params = period(patientId, from, to);
        Map<UUID, String> batchNames = new HashMap<>();
        Map<UUID, String> symptomNames = new HashMap<>();

        Map<UUID, List<RatingEntry>> ratings = new HashMap<>();
        jdbc.query("""
                select r.*, s.name_enc, s.kind, u.username as recorded_by_name
                  from treatment_symptom_ratings r
                  join treatment_symptoms s on s.id = r.symptom_id
                  left join users u on u.id = r.recorded_by
                 where r.dose_id in (%s)
                 order by r.recorded_at""".formatted(DOSES_IN_PERIOD), params,
                rs -> {
                    RatingEntry rating = rating(rs, symptomNames);
                    ratings.computeIfAbsent(rating.doseId(), id -> new ArrayList<>()).add(rating);
                });
        Map<UUID, List<SideEffectView>> effects = new HashMap<>();
        jdbc.query("""
                select * from treatment_side_effects where dose_id in (%s) order by recorded_at""".formatted(DOSES_IN_PERIOD),
                params,
                rs -> {
                    SideEffectView effect = sideEffect(rs);
                    effects.computeIfAbsent(effect.doseId(), id -> new ArrayList<>()).add(effect);
                });

        return jdbc.query("""
                select d.*, b.name_enc as batch_name, b.form, u.username as recorded_by_name
                  from treatment_doses d
                  join treatment_batches b on b.id = d.batch_id
                  left join users u on u.id = d.recorded_by
                 where d.patient_id = :patient and d.taken_at >= :from and d.taken_at < :to
                 order by d.taken_at desc, d.id""", params,
                (rs, n) -> {
                    UUID id = rs.getObject("id", UUID.class);
                    UUID batchId = rs.getObject("batch_id", UUID.class);
                    byte[] encryptedName = rs.getBytes("batch_name");
                    return new DoseEntry(id, batchId,
                            batchNames.computeIfAbsent(batchId, key -> cipher.decrypt(encryptedName)),
                            MedicineForm.valueOf(rs.getString("form")), rs.getBigDecimal("amount"),
                            DoseUnit.valueOf(rs.getString("unit")), Route.valueOf(rs.getString("route")),
                            instant(rs.getTimestamp("taken_at")), rs.getBigDecimal("stock_quantity"),
                            rs.getBigDecimal("thc_mg"), rs.getBigDecimal("cbd_mg"),
                            cipher.decrypt(rs.getBytes("notes_enc")), rs.getString("recorded_by_name"),
                            ratings.getOrDefault(id, List.of()), effects.getOrDefault(id, List.of()));
                });
    }

    @Override
    public List<RatingEntry> checkins(UUID patientId, Instant from, Instant to) {
        Map<UUID, String> symptomNames = new LinkedHashMap<>();
        return jdbc.query("""
                select r.*, s.name_enc, s.kind, u.username as recorded_by_name
                  from treatment_symptom_ratings r
                  join treatment_symptoms s on s.id = r.symptom_id
                  left join users u on u.id = r.recorded_by
                 where r.patient_id = :patient and r.moment = 'CHECKIN'
                   and r.recorded_at >= :from and r.recorded_at < :to
                 order by r.recorded_at desc""", period(patientId, from, to),
                (rs, n) -> rating(rs, symptomNames));
    }

    @Override
    public List<SideEffectView> looseSideEffects(UUID patientId, Instant from, Instant to) {
        return jdbc.query("""
                select * from treatment_side_effects
                 where patient_id = :patient and dose_id is null and recorded_at >= :from and recorded_at < :to
                 order by recorded_at desc""", period(patientId, from, to),
                (rs, n) -> sideEffect(rs));
    }

    private RatingEntry rating(ResultSet rs, Map<UUID, String> symptomNames) throws SQLException {
        UUID symptomId = rs.getObject("symptom_id", UUID.class);
        byte[] encryptedName = rs.getBytes("name_enc");
        return new RatingEntry(rs.getObject("id", UUID.class), rs.getObject("dose_id", UUID.class), symptomId,
                symptomNames.computeIfAbsent(symptomId, key -> cipher.decrypt(encryptedName)),
                SymptomKind.valueOf(rs.getString("kind")), RatingMoment.valueOf(rs.getString("moment")),
                rs.getInt("value"), instant(rs.getTimestamp("recorded_at")), rs.getString("recorded_by_name"));
    }

    private static SideEffectView sideEffect(ResultSet rs) throws SQLException {
        return new SideEffectView(rs.getObject("id", UUID.class), rs.getObject("dose_id", UUID.class),
                SideEffect.valueOf(rs.getString("effect")), Intensity.valueOf(rs.getString("intensity")),
                instant(rs.getTimestamp("recorded_at")));
    }

    private static MapSqlParameterSource period(UUID patientId, Instant from, Instant to) {
        return new MapSqlParameterSource("patient", patientId)
                .addValue("from", Timestamp.from(from))
                .addValue("to", Timestamp.from(to));
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static LocalDate localDate(Date date) {
        return date == null ? null : date.toLocalDate();
    }
}
