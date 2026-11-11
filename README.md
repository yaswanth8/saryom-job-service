# saryom-job-service

Job search for Saryom: each user's private application tracker, and a recruiter
directory shared by everyone.

- **Port** 8088, routes under `/api/jobs/**` (through the API gateway)
- **Database** Supabase Postgres, schema `jobs`, protected by row level security
- **Auth** Firebase ID tokens, plus import tokens for the job-search skill

## Two tiers, one schema

| Tier | Tables | Who can read | Who can write |
|---|---|---|---|
| Private | `application`, `import_token` | the owning user | the owning user |
| Public | `recruiter`, `recruiter_sighting` | anyone | signed-in users (their own entries) |

The split is enforced by Postgres, not only by this service. Every table has
`FORCE ROW LEVEL SECURITY`, and the service connects as a role without
`BYPASSRLS`, so the policies in `V1__init.sql` apply to the service's own
queries. A query that forgot its `where user_id = ?` returns nothing.

The caller's identity reaches the database as transaction-local settings
(`app.user_id`, `app.is_admin`, `app.token_hash`) written by
`RlsTransactionManager` at the start of every transaction.

Contact details in the directory (email, phone, profile link) are returned to
signed-in users only. Anonymous visitors see who a recruiter is and what they
hire for. That is a decision of the API, not of the policies: row level security
filters rows, not columns.

## API

| Method and path | Auth | |
|---|---|---|
| `GET /api/jobs/applications` | sign-in | my applications |
| `GET /api/jobs/applications/summary` | sign-in | counts for the home tile |
| `POST /api/jobs/applications` | sign-in | add one |
| `PUT /api/jobs/applications/{id}` | sign-in | edit |
| `PATCH /api/jobs/applications/{id}/status` | sign-in | move along the pipeline |
| `DELETE /api/jobs/applications/{id}` | sign-in | remove |
| `POST /api/jobs/import` | sign-in **or import token** | upload a tracker |
| `GET /api/jobs/recruiters?category=&q=` | public | browse the directory |
| `GET /api/jobs/recruiters/summary` | public | counts by role and company |
| `POST /api/jobs/recruiters` | sign-in | add a recruiter |
| `DELETE /api/jobs/recruiters/{id}` | contributor or admin | remove an entry |
| `GET/POST /api/jobs/tokens`, `DELETE /api/jobs/tokens/{id}` | sign-in | manage import tokens |

Swagger UI at `/swagger-ui.html`.

## Uploading from the job-search skill

`POST /api/jobs/import` takes the skill's tracker rows exactly as its database
stores them:

```json
{ "shareContacts": true, "jobs": [ { "id": "li-123", "company": "Acme", "role": "Java Developer",
  "status": "Applied", "stage": "Interview", "contactName": "…", "contactEmail": "…" } ] }
```

- Rows are matched on `id`, so uploading again updates rather than duplicates.
- `Skipped` rows stay off the board: they are the skill's memory of jobs the user did
  not want. Their recruiters are still added to the directory, since a job skipped
  for its location or pay says nothing against whoever posted it. Rows skipped as
  spam share nobody.
- An upload never moves an application backwards, and never touches the user's
  own notes.
- Contacts go into the shared directory unless `shareContacts` is `false`.

Authenticate with `Authorization: Bearer sjt_…`, a token the user creates on the
site. A token uploads for its owner and does nothing else.

## Running locally

Needs a Postgres with a role that owns the `jobs` schema and is **not** a
superuser (a superuser bypasses row level security, which would hide bugs):

```sql
CREATE ROLE saryom_jobs LOGIN PASSWORD 'saryom_jobs_local' NOSUPERUSER NOBYPASSRLS;
CREATE SCHEMA jobs AUTHORIZATION saryom_jobs;
ALTER ROLE saryom_jobs SET search_path = jobs;
```

```
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run     # dev profile: tokens are "dev:<uid>"
```

## Tests

`mvn verify`. `RowLevelSecurityTest` runs the migrations and the policies
against a real Postgres through Testcontainers. Without Docker, point it at a
running server instead:

```
JOBS_TEST_PG_URL=jdbc:postgresql://localhost:5432/postgres JOBS_TEST_PG_USER=postgres \
JOBS_TEST_PG_PASSWORD=postgres mvn verify
```

## Deployment

Render, Docker, free plan. Environment:

| Variable | Value |
|---|---|
| `DB_URL` | `jdbc:postgresql://<supabase pooler host>:5432/postgres?sslmode=require` (session mode) |
| `DB_USER` | `saryom_jobs.<project ref>` |
| `DB_PASSWORD` | the role's password |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | same service account as the other services |
| `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE` | `3` |

### Free-plan limits worth knowing

- **Supabase pauses a free project after about a week of too little database
  activity.** `.github/workflows/keepalive.yml` reads the public directory
  summary once a day to prevent that. If the project is ever paused anyway,
  restore it from the Supabase dashboard; the service reconnects on its own.
- The session pooler has a small number of slots, which is why the connection
  pool is capped at 3.
