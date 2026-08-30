-- data-model.md — replaces the binary tooth_state/tooth_status model (002) outright with a full
-- clinical odontogram (research.md D1: no production data exists, so this drops rather than
-- migrates). ToothChart (1:1 patient_record) -> ToothPosition (52 rows/chart, never deleted) ->
-- RootCanal (mutable, soft-delete) / ToothFinding (append-only, correct-via-supersede, research.md
-- D3/D4/D7). DiagnosisCatalogEntry is Flyway-seeded reference data (research.md D5) — INSERTed
-- here, never by application code.

DROP TABLE tooth_state;
DROP TYPE tooth_status;

CREATE TYPE dentition_mode AS ENUM ('PERMANENT', 'DECIDUOUS', 'MIXED');
CREATE TYPE dentition_type AS ENUM ('PERMANENT', 'DECIDUOUS');
CREATE TYPE tooth_type AS ENUM ('INCISOR', 'CANINE', 'PREMOLAR', 'MOLAR');
CREATE TYPE tooth_presence AS ENUM ('PRESENT', 'EXTRACTED', 'CONGENITALLY_MISSING', 'UNERUPTED');
CREATE TYPE root_canal_state AS ENUM ('NEEDS_TREATMENT', 'TREATED', 'UNDERTREATED');
CREATE TYPE diagnosis_category AS ENUM (
    'HARD_TISSUE', 'PULP_PERIAPICAL', 'TRAUMA', 'NON_CARIOUS_LESION',
    'PERIODONTAL_SOFT_TISSUE', 'ERUPTION_MISSING', 'POST_TREATMENT_RESTORATION'
);
CREATE TYPE anatomical_scope AS ENUM ('SURFACE', 'WHOLE_TOOTH', 'ROOT_PERIAPICAL', 'PERIODONTIUM');
CREATE TYPE finding_layer AS ENUM ('DIAGNOSIS', 'EXISTING_STATE');
CREATE TYPE finding_clinical_status AS ENUM ('ACTIVE', 'RESOLVED');
CREATE TYPE finding_record_status AS ENUM ('CURRENT', 'SUPERSEDED');
CREATE TYPE finding_author_role AS ENUM ('DOCTOR', 'ASSISTANT');

CREATE TABLE tooth_chart (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_record_id      UUID NOT NULL UNIQUE REFERENCES patient_record (id),
    dentition_mode         dentition_mode NOT NULL DEFAULT 'PERMANENT',
    -- No DB-level FK to staff_account (owned by auth-service, research.md #5 of 002).
    dentition_mode_set_by  UUID,
    dentition_mode_set_at  TIMESTAMPTZ
);

CREATE TABLE tooth_position (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tooth_chart_id   UUID NOT NULL REFERENCES tooth_chart (id),
    fdi_number       SMALLINT NOT NULL,
    dentition_type   dentition_type NOT NULL,
    tooth_type       tooth_type NOT NULL,
    presence         tooth_presence NOT NULL DEFAULT 'PRESENT',
    presence_date    DATE,
    version          INTEGER NOT NULL DEFAULT 0,
    updated_at       TIMESTAMPTZ NOT NULL,
    updated_by       UUID
);

CREATE UNIQUE INDEX idx_tooth_position_chart_fdi ON tooth_position (tooth_chart_id, fdi_number);

CREATE TABLE root_canal (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tooth_position_id  UUID NOT NULL REFERENCES tooth_position (id),
    name               TEXT NOT NULL,
    state              root_canal_state NOT NULL DEFAULT 'NEEDS_TREATMENT',
    removed            BOOLEAN NOT NULL DEFAULT FALSE,
    version            INTEGER NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ NOT NULL,
    created_by         UUID NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    updated_by         UUID
);

CREATE INDEX idx_root_canal_tooth_position_id ON root_canal (tooth_position_id);

-- Flyway-seeded, read-only via the app (research.md D5, FR-011). Never UPDATEd/DELETEd by a later
-- migration — FR-019 versioning means only new rows get added.
CREATE TABLE diagnosis_catalog_entry (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code                        TEXT NOT NULL UNIQUE,
    name_pl                     TEXT NOT NULL,
    category                    diagnosis_category NOT NULL,
    anatomical_scope            anatomical_scope NOT NULL,
    layer                       finding_layer NOT NULL,
    icd10_code                  TEXT,
    severity_options            TEXT[],
    allowed_for_missing_tooth   BOOLEAN NOT NULL DEFAULT FALSE,
    deciduous_allowed           BOOLEAN NOT NULL DEFAULT TRUE,
    quick_access                BOOLEAN NOT NULL DEFAULT FALSE,
    requires_free_text          BOOLEAN NOT NULL DEFAULT FALSE,
    catalog_version             INTEGER NOT NULL
);

CREATE TABLE tooth_finding (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tooth_position_id           UUID NOT NULL REFERENCES tooth_position (id),
    diagnosis_catalog_entry_id  UUID NOT NULL REFERENCES diagnosis_catalog_entry (id),
    -- ToothSurface.java enum names, plain TEXT[] rather than a native Postgres enum array —
    -- validated in ToothFindingService (needs the joined catalog entry's anatomicalScope anyway,
    -- so isn't a bare DB constraint either way), keeping the JPA array mapping unambiguous.
    surfaces                    TEXT[],
    root_canal_id               UUID REFERENCES root_canal (id),
    severity                    TEXT,
    free_text_description       TEXT,
    note                        VARCHAR(1000),
    diagnosis_date              DATE NOT NULL,
    resolved_date                DATE,
    clinical_status              finding_clinical_status NOT NULL DEFAULT 'ACTIVE',
    record_status                 finding_record_status NOT NULL DEFAULT 'CURRENT',
    supersedes_finding_id         UUID REFERENCES tooth_finding (id),
    -- No DB-level FK to staff_account (owned by auth-service, research.md #5 of 002).
    author_account_id             UUID NOT NULL,
    author_role                   finding_author_role NOT NULL,
    created_at                    TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_tooth_finding_tooth_position_id ON tooth_finding (tooth_position_id);
CREATE INDEX idx_tooth_finding_position_record_status ON tooth_finding (tooth_position_id, record_status);

-- research.md D7 — at most one correction can ever be inserted per superseded finding; a second
-- concurrent correction attempt hits this constraint (mapped to 409 by GlobalExceptionHandler).
CREATE UNIQUE INDEX idx_tooth_finding_supersedes_unique
    ON tooth_finding (supersedes_finding_id) WHERE supersedes_finding_id IS NOT NULL;

GRANT SELECT, INSERT, UPDATE ON tooth_chart TO patient_service_app;
GRANT SELECT, INSERT, UPDATE ON tooth_position TO patient_service_app;
GRANT SELECT, INSERT, UPDATE ON root_canal TO patient_service_app;
GRANT SELECT ON diagnosis_catalog_entry TO patient_service_app;
-- UPDATE is required for the supersede operation (research.md D3) — it flips the superseded row's
-- record_status in place; no other column is ever updated in place, and no DELETE is ever granted.
GRANT SELECT, INSERT, UPDATE ON tooth_finding TO patient_service_app;

-- FR-015 catalog seed (research.md D5) — working names ported verbatim from
-- mockup/odontogram-mockup.html's DX array. FR-011a's four "inne rozpoznanie" fallback rows (one
-- per anatomical_scope, session 2026-08-30 piąta tura) replace the mockup's single free-scope
-- OTHER row; each is filed under the FR-014 category whose subject matter it's the closest,
-- non-exclusive catch-all for (SURFACE -> hard-tissue findings are the most common surface-scoped
-- diagnoses; WHOLE_TOOTH -> eruption/missing-tooth findings are inherently whole-tooth facts;
-- ROOT_PERIAPICAL/PERIODONTIUM have a direct, unambiguous category match) — anatomicalScope itself,
-- not category, is what the four rows exist to vary.
INSERT INTO diagnosis_catalog_entry
    (code, name_pl, category, anatomical_scope, layer, icd10_code, severity_options,
     allowed_for_missing_tooth, deciduous_allowed, quick_access, requires_free_text, catalog_version)
VALUES
    ('K02.0a', 'Próchnica początkowa (plamka próchnicowa)', 'HARD_TISSUE', 'SURFACE', 'DIAGNOSIS', 'K02.0', NULL, FALSE, TRUE, FALSE, FALSE, 1),
    ('K02.0b', 'Próchnica szkliwa', 'HARD_TISSUE', 'SURFACE', 'DIAGNOSIS', 'K02.0', NULL, FALSE, TRUE, TRUE, FALSE, 1),
    ('K02.1', 'Próchnica zębiny', 'HARD_TISSUE', 'SURFACE', 'DIAGNOSIS', 'K02.1', NULL, FALSE, TRUE, TRUE, FALSE, 1),
    ('K02.1d', 'Próchnica głęboka', 'HARD_TISSUE', 'SURFACE', 'DIAGNOSIS', 'K02.1', NULL, FALSE, TRUE, TRUE, FALSE, 1),
    ('K02.1s', 'Próchnica wtórna (przy istniejącym wypełnieniu)', 'HARD_TISSUE', 'SURFACE', 'DIAGNOSIS', 'K02.1', NULL, FALSE, TRUE, FALSE, FALSE, 1),
    ('K02.2', 'Próchnica korzenia', 'HARD_TISSUE', 'SURFACE', 'DIAGNOSIS', 'K02.2', NULL, FALSE, TRUE, FALSE, FALSE, 1),
    ('K04.0r', 'Zapalenie miazgi odwracalne', 'PULP_PERIAPICAL', 'WHOLE_TOOTH', 'DIAGNOSIS', 'K04.0', NULL, FALSE, TRUE, FALSE, FALSE, 1),
    ('K04.0i', 'Zapalenie miazgi nieodwracalne', 'PULP_PERIAPICAL', 'WHOLE_TOOTH', 'DIAGNOSIS', 'K04.0', NULL, FALSE, TRUE, FALSE, FALSE, 1),
    ('K04.1', 'Martwica miazgi', 'PULP_PERIAPICAL', 'WHOLE_TOOTH', 'DIAGNOSIS', 'K04.1', NULL, FALSE, TRUE, FALSE, FALSE, 1),
    ('K04.4', 'Zapalenie tkanek okołowierzchołkowych', 'PULP_PERIAPICAL', 'ROOT_PERIAPICAL', 'DIAGNOSIS', 'K04.4', ARRAY['ostre','przewlekłe'], FALSE, TRUE, FALSE, FALSE, 1),
    ('K04.7', 'Ropień okołowierzchołkowy', 'PULP_PERIAPICAL', 'ROOT_PERIAPICAL', 'DIAGNOSIS', 'K04.7', NULL, FALSE, TRUE, FALSE, FALSE, 1),
    ('K04.8', 'Torbiel korzeniowa', 'PULP_PERIAPICAL', 'ROOT_PERIAPICAL', 'DIAGNOSIS', 'K04.8', NULL, FALSE, TRUE, FALSE, FALSE, 1),
    ('S02.51', 'Złamanie korony', 'TRAUMA', 'SURFACE', 'DIAGNOSIS', 'S02.51', NULL, FALSE, TRUE, FALSE, FALSE, 1),
    ('S02.53', 'Złamanie korzenia', 'TRAUMA', 'ROOT_PERIAPICAL', 'DIAGNOSIS', 'S02.53', NULL, FALSE, TRUE, FALSE, FALSE, 1),
    ('K03.81', 'Pęknięcie zęba', 'TRAUMA', 'WHOLE_TOOTH', 'DIAGNOSIS', 'K03.81', NULL, FALSE, TRUE, FALSE, FALSE, 1),
    ('S02.52', 'Odłamanie fragmentu korony', 'TRAUMA', 'SURFACE', 'DIAGNOSIS', 'S02.52', NULL, FALSE, TRUE, FALSE, FALSE, 1),
    ('K03.0', 'Starcie patologiczne (atrycja)', 'NON_CARIOUS_LESION', 'SURFACE', 'DIAGNOSIS', 'K03.0', ARRAY['1°','2°','3°','4°'], FALSE, TRUE, FALSE, FALSE, 1),
    ('K03.1', 'Abrazja', 'NON_CARIOUS_LESION', 'SURFACE', 'DIAGNOSIS', 'K03.1', NULL, FALSE, TRUE, FALSE, FALSE, 1),
    ('K03.2', 'Erozja', 'NON_CARIOUS_LESION', 'SURFACE', 'DIAGNOSIS', 'K03.2', NULL, FALSE, TRUE, FALSE, FALSE, 1),
    ('K03.19', 'Abfrakcja', 'NON_CARIOUS_LESION', 'SURFACE', 'DIAGNOSIS', NULL, NULL, FALSE, TRUE, FALSE, FALSE, 1),
    ('K03.8', 'Nadwrażliwość zębiny', 'NON_CARIOUS_LESION', 'SURFACE', 'DIAGNOSIS', 'K03.8', NULL, FALSE, TRUE, FALSE, FALSE, 1),
    ('K05.1', 'Zapalenie dziąsła', 'PERIODONTAL_SOFT_TISSUE', 'PERIODONTIUM', 'DIAGNOSIS', 'K05.1', NULL, FALSE, TRUE, TRUE, FALSE, 1),
    ('K05.3', 'Zapalenie przyzębia', 'PERIODONTAL_SOFT_TISSUE', 'PERIODONTIUM', 'DIAGNOSIS', 'K05.3', ARRAY['I°','II°','III°','IV°'], FALSE, TRUE, FALSE, FALSE, 1),
    ('K06.0', 'Recesja dziąsłowa', 'PERIODONTAL_SOFT_TISSUE', 'PERIODONTIUM', 'DIAGNOSIS', 'K06.0', ARRAY['klasa I','klasa II','klasa III','klasa IV'], FALSE, TRUE, FALSE, FALSE, 1),
    ('K03.6', 'Kamień nazębny', 'PERIODONTAL_SOFT_TISSUE', 'PERIODONTIUM', 'DIAGNOSIS', 'K03.6', NULL, FALSE, TRUE, FALSE, FALSE, 1),
    ('K05.31', 'Kieszonka przyzębna', 'PERIODONTAL_SOFT_TISSUE', 'PERIODONTIUM', 'DIAGNOSIS', NULL, ARRAY['4–5 mm','6–7 mm','≥8 mm'], FALSE, TRUE, FALSE, FALSE, 1),
    ('K01.0', 'Ząb zatrzymany', 'ERUPTION_MISSING', 'WHOLE_TOOTH', 'DIAGNOSIS', 'K01.0', NULL, FALSE, TRUE, FALSE, FALSE, 1),
    ('K00.6', 'Ząb niewyrznięty', 'ERUPTION_MISSING', 'WHOLE_TOOTH', 'DIAGNOSIS', 'K00.6', NULL, FALSE, TRUE, FALSE, FALSE, 1),
    ('K07.3', 'Nieprawidłowe ustawienie / rotacja', 'ERUPTION_MISSING', 'WHOLE_TOOTH', 'DIAGNOSIS', 'K07.3', NULL, FALSE, TRUE, FALSE, FALSE, 1),
    ('K00.0', 'Wrodzony brak zęba (agenezja)', 'ERUPTION_MISSING', 'WHOLE_TOOTH', 'DIAGNOSIS', 'K00.0', NULL, TRUE, TRUE, FALSE, FALSE, 1),
    ('EXTR', 'Ząb usunięty (stan po ekstrakcji)', 'ERUPTION_MISSING', 'WHOLE_TOOTH', 'EXISTING_STATE', NULL, NULL, TRUE, TRUE, TRUE, FALSE, 1),
    ('FILL', 'Wypełnienie', 'POST_TREATMENT_RESTORATION', 'SURFACE', 'EXISTING_STATE', NULL, NULL, FALSE, TRUE, TRUE, FALSE, 1),
    ('FILLT', 'Wypełnienie tymczasowe', 'POST_TREATMENT_RESTORATION', 'SURFACE', 'EXISTING_STATE', NULL, NULL, FALSE, TRUE, FALSE, FALSE, 1),
    ('SEAL', 'Uszczelnienie bruzd (lak)', 'POST_TREATMENT_RESTORATION', 'SURFACE', 'EXISTING_STATE', NULL, NULL, FALSE, TRUE, TRUE, FALSE, 1),
    ('ENDO', 'Leczenie kanałowe (stan po)', 'POST_TREATMENT_RESTORATION', 'ROOT_PERIAPICAL', 'EXISTING_STATE', NULL, NULL, FALSE, TRUE, TRUE, FALSE, 1),
    ('POST', 'Wkład koronowo-korzeniowy', 'POST_TREATMENT_RESTORATION', 'ROOT_PERIAPICAL', 'EXISTING_STATE', NULL, NULL, FALSE, FALSE, FALSE, FALSE, 1),
    ('CROWN', 'Korona protetyczna', 'POST_TREATMENT_RESTORATION', 'WHOLE_TOOTH', 'EXISTING_STATE', NULL, NULL, FALSE, FALSE, FALSE, FALSE, 1),
    ('VENEER', 'Licówka', 'POST_TREATMENT_RESTORATION', 'SURFACE', 'EXISTING_STATE', NULL, NULL, FALSE, FALSE, FALSE, FALSE, 1),
    ('PONTIC', 'Przęsło mostu', 'POST_TREATMENT_RESTORATION', 'WHOLE_TOOTH', 'EXISTING_STATE', NULL, NULL, TRUE, FALSE, FALSE, FALSE, 1),
    ('IMPL', 'Implant', 'POST_TREATMENT_RESTORATION', 'WHOLE_TOOTH', 'EXISTING_STATE', NULL, NULL, TRUE, FALSE, FALSE, FALSE, 1),
    ('ABUT', 'Ząb filarowy protezy', 'POST_TREATMENT_RESTORATION', 'WHOLE_TOOTH', 'EXISTING_STATE', NULL, NULL, TRUE, FALSE, FALSE, FALSE, 1),
    ('OTHER_SURFACE', 'Inne rozpoznanie (powierzchnia zęba)', 'HARD_TISSUE', 'SURFACE', 'DIAGNOSIS', NULL, NULL, FALSE, TRUE, FALSE, TRUE, 1),
    ('OTHER_WHOLE_TOOTH', 'Inne rozpoznanie (cały ząb)', 'ERUPTION_MISSING', 'WHOLE_TOOTH', 'DIAGNOSIS', NULL, NULL, TRUE, TRUE, FALSE, TRUE, 1),
    ('OTHER_ROOT_PERIAPICAL', 'Inne rozpoznanie (korzeń / okolica wierzchołka)', 'PULP_PERIAPICAL', 'ROOT_PERIAPICAL', 'DIAGNOSIS', NULL, NULL, FALSE, TRUE, FALSE, TRUE, 1),
    ('OTHER_PERIODONTIUM', 'Inne rozpoznanie (przyzębie)', 'PERIODONTAL_SOFT_TISSUE', 'PERIODONTIUM', 'DIAGNOSIS', NULL, NULL, FALSE, TRUE, FALSE, TRUE, 1);
