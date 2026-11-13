-- What the job-search skill (2.0 and later) records about each posting
-- beyond the basics: its text, when it went up, how it takes applications,
-- what it says about sponsorship, how the skill found it, why it has no
-- contact, and what the user should do next. All optional: rows typed in on
-- the site and rows from older uploads simply leave them empty.
ALTER TABLE application
    ADD COLUMN description  TEXT,
    ADD COLUMN posted_at    TIMESTAMPTZ,
    ADD COLUMN easy_apply   BOOLEAN,
    ADD COLUMN apply_url    TEXT,
    ADD COLUMN ats          TEXT,
    ADD COLUMN sponsorship  TEXT,
    ADD COLUMN found_via    TEXT,
    ADD COLUMN contact_note TEXT,
    ADD COLUMN next_step    TEXT,
    ADD CONSTRAINT ck_application_sponsorship CHECK (sponsorship IN ('VERIFIED', 'NEEDS_CONFIRMATION', 'EXCLUDED')),
    ADD CONSTRAINT ck_application_found_via CHECK (found_via IN ('APIFY', 'BROWSER')),
    ADD CONSTRAINT ck_application_next_step CHECK (next_step IN ('READY', 'REACH_OUT', 'COMPANY_SITE', 'YOUR_CALL', 'QUEUED'));

-- "Stretch": the posting asks for more years than the candidate has, within
-- the ceiling they set.
ALTER TABLE application DROP CONSTRAINT ck_application_fit;
ALTER TABLE application ADD CONSTRAINT ck_application_fit CHECK (fit IN ('HIGH', 'MEDIUM', 'STRETCH', 'LOW'));
