-- job-service schema, with row level security.
--
-- Two tiers live side by side here:
--   private  application, import_token   visible only to the user who owns the row
--   public   recruiter, recruiter_sighting   readable by anyone, writable by contributors
--
-- The service connects as a role that owns these tables but has NOBYPASSRLS,
-- and every table is FORCE ROW LEVEL SECURITY, so the policies bind the service
-- itself. A query that forgets its "where user_id = ?" returns nothing rather
-- than someone else's job search.
--
-- The caller's identity arrives as transaction-local settings, written by the
-- service at the start of every transaction (see RlsTransactionManager):
--   app.user_id     verified Firebase uid, empty for anonymous
--   app.is_admin    'true' for moderators
--   app.token_hash  SHA-256 of an import token being presented, if any
-- Transaction-local matters: connections are pooled, and a session-level
-- setting would leak one user's identity into the next request.

CREATE FUNCTION request_uid() RETURNS text
    LANGUAGE sql STABLE
AS $$ SELECT nullif(current_setting('app.user_id', true), '') $$;

CREATE FUNCTION request_is_admin() RETURNS boolean
    LANGUAGE sql STABLE
AS $$ SELECT coalesce(current_setting('app.is_admin', true), '') = 'true' $$;

CREATE FUNCTION request_token_hash() RETURNS text
    LANGUAGE sql STABLE
AS $$ SELECT nullif(current_setting('app.token_hash', true), '') $$;

-- ---------------------------------------------------------------------------
-- Private tier: a user's own applications
-- ---------------------------------------------------------------------------

CREATE TABLE application (
    id              UUID PRIMARY KEY,
    user_id         TEXT        NOT NULL,
    -- The job-search skill's tracker id (li-…, dice-…, indeed-…, web-…), so a
    -- re-upload updates the row it created instead of adding a second one.
    external_id     TEXT,
    company         TEXT        NOT NULL,
    vendor          TEXT,
    role            TEXT        NOT NULL,
    location        TEXT,
    work_type       TEXT,
    employment_type TEXT,
    pay             TEXT,
    source          TEXT,
    link            TEXT,
    fit             TEXT,
    status          TEXT        NOT NULL,
    notes           TEXT,
    user_note       TEXT,
    contact_name    TEXT,
    contact_title   TEXT,
    contact_email   TEXT,
    contact_phone   TEXT,
    contact_url     TEXT,
    found_on        DATE,
    applied_on      DATE,
    follow_up_on    DATE,
    applied_by      TEXT,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    version         BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uq_application_user_external UNIQUE (user_id, external_id),
    CONSTRAINT ck_application_status CHECK (status IN (
        'SAVED', 'APPLIED', 'SCREENING', 'INTERVIEW', 'OFFER',
        'REJECTED', 'WITHDRAWN', 'NO_RESPONSE', 'DECLINED')),
    CONSTRAINT ck_application_work_type CHECK (work_type IN ('REMOTE', 'HYBRID', 'ONSITE')),
    CONSTRAINT ck_application_fit CHECK (fit IN ('HIGH', 'MEDIUM', 'LOW'))
);

CREATE INDEX idx_application_user_updated ON application (user_id, updated_at DESC);

ALTER TABLE application ENABLE ROW LEVEL SECURITY;
ALTER TABLE application FORCE ROW LEVEL SECURITY;

CREATE POLICY application_owner ON application
    FOR ALL
    USING (user_id = request_uid())
    WITH CHECK (user_id = request_uid());

-- ---------------------------------------------------------------------------
-- Private tier: tokens that let the job-search skill upload on a user's behalf
-- ---------------------------------------------------------------------------

CREATE TABLE import_token (
    id           UUID PRIMARY KEY,
    user_id      TEXT        NOT NULL,
    label        TEXT        NOT NULL,
    -- Only the hash is stored; the token itself is shown once, on creation.
    token_hash   TEXT        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    last_used_at TIMESTAMPTZ,
    CONSTRAINT uq_import_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_import_token_user ON import_token (user_id);

ALTER TABLE import_token ENABLE ROW LEVEL SECURITY;
ALTER TABLE import_token FORCE ROW LEVEL SECURITY;

CREATE POLICY import_token_owner ON import_token
    FOR ALL
    USING (user_id = request_uid())
    WITH CHECK (user_id = request_uid());

-- Signing in with a token: the row can be read by whoever presents its hash,
-- and by nobody else. It cannot be listed, only looked up by a value the
-- caller already holds.
CREATE POLICY import_token_presented ON import_token
    FOR SELECT
    USING (token_hash = request_token_hash());

-- ---------------------------------------------------------------------------
-- Public tier: the shared recruiter directory
-- ---------------------------------------------------------------------------

CREATE TABLE recruiter (
    id             UUID PRIMARY KEY,
    -- Lower-cased email, else profile URL, else name+company: one row per person
    -- however many users come across them.
    dedupe_key     TEXT        NOT NULL,
    full_name      TEXT        NOT NULL,
    title          TEXT,
    company        TEXT,
    email          TEXT,
    phone          TEXT,
    profile_url    TEXT,
    contributed_by TEXT        NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_recruiter_dedupe UNIQUE (dedupe_key)
);

ALTER TABLE recruiter ENABLE ROW LEVEL SECURITY;
ALTER TABLE recruiter FORCE ROW LEVEL SECURITY;

CREATE POLICY recruiter_public_read ON recruiter
    FOR SELECT
    USING (true);

CREATE POLICY recruiter_contribute ON recruiter
    FOR INSERT
    WITH CHECK (request_uid() IS NOT NULL AND contributed_by = request_uid());

CREATE POLICY recruiter_contributor_update ON recruiter
    FOR UPDATE
    USING (contributed_by = request_uid() OR request_is_admin())
    WITH CHECK (contributed_by = request_uid() OR request_is_admin());

CREATE POLICY recruiter_contributor_delete ON recruiter
    FOR DELETE
    USING (contributed_by = request_uid() OR request_is_admin());

-- One row per (recruiter, role, contributor): what the recruiter was seen
-- hiring for. This is what "recruiters for Java roles" is answered from.
CREATE TABLE recruiter_sighting (
    id             UUID PRIMARY KEY,
    recruiter_id   UUID        NOT NULL REFERENCES recruiter (id) ON DELETE CASCADE,
    role_title     TEXT        NOT NULL,
    role_key       TEXT        NOT NULL,
    role_category  TEXT        NOT NULL,
    company        TEXT,
    location       TEXT,
    contributed_by TEXT        NOT NULL,
    seen_on        DATE        NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_sighting UNIQUE (recruiter_id, contributed_by, role_key)
);

CREATE INDEX idx_sighting_category ON recruiter_sighting (role_category, seen_on DESC);
CREATE INDEX idx_sighting_recruiter ON recruiter_sighting (recruiter_id);

ALTER TABLE recruiter_sighting ENABLE ROW LEVEL SECURITY;
ALTER TABLE recruiter_sighting FORCE ROW LEVEL SECURITY;

CREATE POLICY sighting_public_read ON recruiter_sighting
    FOR SELECT
    USING (true);

CREATE POLICY sighting_contribute ON recruiter_sighting
    FOR INSERT
    WITH CHECK (request_uid() IS NOT NULL AND contributed_by = request_uid());

CREATE POLICY sighting_contributor_update ON recruiter_sighting
    FOR UPDATE
    USING (contributed_by = request_uid() OR request_is_admin())
    WITH CHECK (contributed_by = request_uid() OR request_is_admin());

CREATE POLICY sighting_contributor_delete ON recruiter_sighting
    FOR DELETE
    USING (contributed_by = request_uid() OR request_is_admin());
