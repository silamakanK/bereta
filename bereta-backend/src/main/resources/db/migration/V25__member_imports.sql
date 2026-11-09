-- Import de membres par fichier ou photo. Le fichier n'est jamais stocké : seule son empreinte SHA-256
-- est conservée (alerte « déjà importé »). Les lignes d'aperçu n'existent que pendant le brouillon.

CREATE TABLE member_imports (
    id UUID PRIMARY KEY,
    association_id UUID NOT NULL REFERENCES associations (id),
    -- Caisse de destination : même logique que les inscriptions (caisse locale vide = caisse principale).
    primary_fund_id UUID NOT NULL REFERENCES primary_funds (id),
    local_fund_id UUID REFERENCES local_funds (id),
    created_by_user_id UUID REFERENCES user_accounts (id) ON DELETE SET NULL,
    source_type VARCHAR(20) NOT NULL,
    file_fingerprint VARCHAR(64) NOT NULL,
    status VARCHAR(20) DEFAULT 'BROUILLON' NOT NULL,
    row_count INTEGER DEFAULT 0 NOT NULL,
    created_count INTEGER DEFAULT 0 NOT NULL,
    ignored_count INTEGER DEFAULT 0 NOT NULL,
    enrolled_count INTEGER DEFAULT 0 NOT NULL,
    version BIGINT DEFAULT 0 NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    validated_at TIMESTAMP WITH TIME ZONE,
    cancelled_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_member_imports_status CHECK (status IN ('BROUILLON', 'VALIDE', 'ANNULE')),
    CONSTRAINT ck_member_imports_source CHECK (source_type IN ('CSV', 'EXCEL', 'PDF', 'PHOTO'))
);

CREATE INDEX idx_member_imports_association ON member_imports (association_id, created_at);
CREATE INDEX idx_member_imports_fingerprint ON member_imports (association_id, file_fingerprint);
CREATE INDEX idx_member_imports_status ON member_imports (status, updated_at);

CREATE TABLE member_import_rows (
    id UUID PRIMARY KEY,
    import_id UUID NOT NULL REFERENCES member_imports (id) ON DELETE CASCADE,
    line_number INTEGER NOT NULL,
    -- Valeurs lues dans le document (jamais modifiées) et valeurs corrigées par l'utilisateur.
    read_first_name VARCHAR(100),
    read_last_name VARCHAR(100),
    read_phone VARCHAR(40),
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    phone VARCHAR(40),
    phone_e164 VARCHAR(20),
    confidence VARCHAR(10),
    remark VARCHAR(255),
    status VARCHAR(20) NOT NULL,
    included BOOLEAN DEFAULT TRUE NOT NULL,
    confirmed BOOLEAN DEFAULT FALSE NOT NULL,
    duplicate_member_id UUID REFERENCES members (id) ON DELETE SET NULL,
    duplicate_of_line INTEGER,
    duplicate_action VARCHAR(10),
    CONSTRAINT ck_member_import_rows_status CHECK (status IN ('PRETE', 'A_VERIFIER', 'DOUBLON')),
    CONSTRAINT ck_member_import_rows_confidence CHECK (confidence IS NULL OR confidence IN ('HAUTE', 'MOYENNE', 'BASSE')),
    CONSTRAINT ck_member_import_rows_action CHECK (duplicate_action IS NULL OR duplicate_action IN ('IGNORER', 'CREER'))
);

CREATE INDEX idx_member_import_rows_import ON member_import_rows (import_id, line_number);

-- Téléphone normalisé (E.164) : base de la détection des doublons. members.phone reste tel que saisi.
ALTER TABLE members ADD COLUMN phone_e164 VARCHAR(20);
ALTER TABLE members ADD COLUMN import_id UUID REFERENCES member_imports (id);
CREATE INDEX idx_members_association_phone_e164 ON members (association_id, phone_e164);
CREATE INDEX idx_members_import ON members (import_id);
