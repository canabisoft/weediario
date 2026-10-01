-- Acompanhamento de tratamento com cannabis medicinal. Dado pessoal sensível (LGPD, art. 11):
-- * nada daqui aparece em feed, ranking, perfil público ou notificações de outras pessoas;
-- * textos livres (nomes, observações) chegam cifrados pela aplicação (AES-GCM) nas colunas *_enc;
-- * apagar a conta, o perfil ou revogar o consentimento apaga tudo em cascata.
-- O sistema só registra o que o paciente usou e sentiu: não há tabela de dose recomendada.

create table treatment_consents (
    user_id       uuid        primary key references users (id) on delete cascade,
    terms_version varchar(20) not null,
    accepted_at   timestamptz not null
);

create table treatment_patients (
    id         uuid        primary key,
    owner_id   uuid        not null references users (id) on delete cascade,
    name_enc   bytea       not null,
    notes_enc  bytea,
    created_at timestamptz not null
);

create index ix_treatment_patients_owner on treatment_patients (owner_id);

create table treatment_caregivers (
    patient_id  uuid        not null references treatment_patients (id) on delete cascade,
    user_id     uuid        not null references users (id) on delete cascade,
    accepted    boolean     not null default false,
    invited_at  timestamptz not null,
    accepted_at timestamptz,
    primary key (patient_id, user_id)
);

create index ix_treatment_caregivers_user on treatment_caregivers (user_id);

create table treatment_symptoms (
    id         uuid        primary key,
    patient_id uuid        not null references treatment_patients (id) on delete cascade,
    name_enc   bytea       not null,
    kind       varchar(10) not null,
    archived   boolean     not null default false,
    created_at timestamptz not null,
    constraint ck_treatment_symptoms_kind check (kind in ('SCALE', 'COUNT'))
);

create index ix_treatment_symptoms_patient on treatment_symptoms (patient_id);

create table treatment_batches (
    id               uuid          primary key,
    patient_id       uuid          not null references treatment_patients (id) on delete cascade,
    name_enc         bytea         not null,
    form             varchar(20)   not null,
    origin           varchar(20)   not null,
    thc              numeric(9, 3) check (thc >= 0),
    cbd              numeric(9, 3) check (cbd >= 0),
    drops_per_ml     numeric(5, 2) check (drops_per_ml between 5 and 60),
    initial_quantity numeric(12, 3) not null check (initial_quantity > 0),
    manufactured_on  date,
    expires_on       date,
    notes_enc        bytea,
    archived         boolean       not null default false,
    created_at       timestamptz   not null,
    constraint ck_treatment_batches_form
        check (form in ('OIL', 'TINCTURE', 'FLOWER', 'GUMMY', 'CAPSULE', 'TOPICAL', 'EXTRACT')),
    constraint ck_treatment_batches_origin
        check (origin in ('HOME_GROWN', 'ASSOCIATION', 'PHARMACY', 'IMPORTED', 'OTHER'))
);

create index ix_treatment_batches_patient on treatment_batches (patient_id);

-- Correções de estoque fora das doses. O saldo é: inicial − doses + ajustes (nunca guardado).
create table treatment_stock_adjustments (
    id          uuid           primary key,
    batch_id    uuid           not null references treatment_batches (id) on delete cascade,
    kind        varchar(12)    not null,
    quantity    numeric(12, 3) not null check (quantity <> 0),
    occurred_at timestamptz    not null,
    recorded_by uuid           references users (id) on delete set null,
    constraint ck_treatment_adjustments_kind check (kind in ('ADJUSTMENT', 'DISCARD'))
);

create index ix_treatment_adjustments_batch on treatment_stock_adjustments (batch_id);

create table treatment_doses (
    id             uuid           primary key,
    patient_id     uuid           not null references treatment_patients (id) on delete cascade,
    batch_id       uuid           not null references treatment_batches (id) on delete cascade,
    amount         numeric(12, 3) not null check (amount > 0),
    unit           varchar(12)    not null,
    route          varchar(12)    not null,
    taken_at       timestamptz    not null,
    -- Calculados no registro a partir do rótulo (o rótulo pode ser corrigido depois sem reescrever o histórico).
    stock_quantity numeric(12, 3),
    thc_mg         numeric(12, 3),
    cbd_mg         numeric(12, 3),
    notes_enc      bytea,
    recorded_by    uuid           references users (id) on delete set null,
    created_at     timestamptz    not null,
    constraint ck_treatment_doses_unit check (unit in ('DROP', 'ML', 'G', 'UNIT', 'APPLICATION')),
    constraint ck_treatment_doses_route check (route in ('SUBLINGUAL', 'ORAL', 'INHALED', 'TOPICAL', 'OTHER'))
);

create index ix_treatment_doses_patient on treatment_doses (patient_id, taken_at desc);
create index ix_treatment_doses_batch on treatment_doses (batch_id);

create table treatment_symptom_ratings (
    id          uuid        primary key,
    patient_id  uuid        not null references treatment_patients (id) on delete cascade,
    symptom_id  uuid        not null references treatment_symptoms (id) on delete cascade,
    dose_id     uuid        references treatment_doses (id) on delete cascade,
    moment      varchar(10) not null,
    value       integer     not null check (value between 0 and 1000),
    recorded_at timestamptz not null,
    recorded_by uuid        references users (id) on delete set null,
    constraint ck_treatment_ratings_moment check (moment in ('BEFORE', 'AFTER', 'CHECKIN')),
    constraint ck_treatment_ratings_dose check ((moment = 'CHECKIN') = (dose_id is null))
);

create unique index ux_treatment_ratings_dose on treatment_symptom_ratings (dose_id, symptom_id, moment)
    where dose_id is not null;
create index ix_treatment_ratings_patient on treatment_symptom_ratings (patient_id, recorded_at);

create table treatment_side_effects (
    id          uuid        primary key,
    patient_id  uuid        not null references treatment_patients (id) on delete cascade,
    dose_id     uuid        references treatment_doses (id) on delete cascade,
    effect      varchar(20) not null,
    intensity   varchar(10) not null,
    recorded_at timestamptz not null,
    recorded_by uuid        references users (id) on delete set null,
    constraint ck_treatment_side_effects_effect check (effect in ('DIZZINESS', 'DROWSINESS', 'DRY_MOUTH', 'RED_EYES',
        'TACHYCARDIA', 'ANXIETY', 'HUNGER', 'NAUSEA', 'HEADACHE', 'OTHER')),
    constraint ck_treatment_side_effects_intensity check (intensity in ('MILD', 'MODERATE', 'SEVERE'))
);

create index ix_treatment_side_effects_patient on treatment_side_effects (patient_id, recorded_at);
