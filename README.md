# AI Study Companion

An AI-powered learning workspace: create a Space and Project, upload PDF study
material, learn with a grounded AI Tutor, take an adaptive quiz, track concept
mastery and growth over time, and get a concrete recommendation for what to do
next — with an Admin Dashboard for platform-wide visibility.

Built with **Spring Boot 3 (Java 17)** on the backend and **React 18 + Vite**
on the frontend.

> This project was generated as a full working scaffold implementing the
> complete learning loop end to end, with a deliberately honest set of
> simplifications documented in [Known Limitations](#known-limitations) below
> rather than glossed over. See that section and
> [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for what's simplified and why.

---

## 1. Quick start (local, no Docker)

### Prerequisites
- Java 17+
- Maven 3.8+ (or use the included `mvnw` if you add one — a plain system Maven works fine)
- Node.js 18+ and npm

### Backend

```bash
cd backend
mvn spring-boot:run
```

The backend starts on `http://localhost:8080`, using an embedded/file-based
H2 database (no setup required) and **AI mock mode enabled by default**
(`AI_MOCK_MODE=true`) — so the entire product works out of the box with
**no API key**. A default admin account is seeded on first run:

```
email:    admin@studycompanion.local
password: ChangeMe123!
```

To use a real model instead of mock mode:

```bash
export AI_MOCK_MODE=false
export ANTHROPIC_API_KEY=sk-ant-...
mvn spring-boot:run
```

### Frontend

```bash
cd frontend
npm install
npm run dev
```

Opens on `http://localhost:5173` and proxies `/api` to `http://localhost:8080`
(see `vite.config.js`). Register a new account and go.

## 2. Quick start (Docker Compose)

```bash
docker compose up --build
```

- Frontend: `http://localhost:8081`
- Backend API: `http://localhost:8080`

Set `ANTHROPIC_API_KEY` and `AI_MOCK_MODE=false` as environment variables
before running `docker compose up` to use a real model instead of mock mode.

## 3. Configuration reference

All configuration is environment-variable driven (`backend/src/main/resources/application.yml`).
No secrets are committed to the repository.

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL` / `DB_DRIVER` / `DB_USER` / `DB_PASSWORD` | H2 file DB | Swap to Postgres for a closer-to-production setup (see below) |
| `JWT_SECRET` | dev placeholder | **Change this in any real deployment** |
| `JWT_EXPIRATION_MS` | 86400000 (24h) | Token lifetime |
| `ALLOWED_ORIGINS` | `http://localhost:5173` | CORS allow-list |
| `UPLOADS_DIR` | `./uploads` | Where raw PDFs are stored |
| `AI_PROVIDER` | `anthropic` | Provider seam (see Architecture doc) |
| `AI_MOCK_MODE` | `true` | Deterministic rule-based AI instead of a live model call |
| `ANTHROPIC_API_KEY` | (empty) | Required only when `AI_MOCK_MODE=false` |
| `ANTHROPIC_MODEL` | `claude-sonnet-4-6` | Model id |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | seeded defaults | Change for anything beyond local evaluation |

### Switching to Postgres

```bash
export DB_URL=jdbc:postgresql://localhost:5432/studycompanion
export DB_DRIVER=org.postgresql.Driver
export DB_USER=postgres
export DB_PASSWORD=postgres
```

`ddl-auto: update` will create the schema automatically on first run (fine
for a prototype; a real deployment would use versioned migrations, e.g.
Flyway — see Future Improvements).

## 4. Testing

```bash
cd backend
mvn test
```

Covers:
- **Security / isolation**: registration, login, and — importantly — that
  one user's JWT cannot create or read resources inside another user's Space
  (`AuthAndIsolationIntegrationTest`), plus that non-admin JWTs are rejected
  from `/api/admin/**`.
- **Adaptive mastery logic**: the core business rule that a correct answer on
  a *harder* question moves mastery up more than a correct answer on an easy
  one, and a wrong answer on an *easy* question is stronger negative evidence
  than struggling on a hard one (`MasteryServiceTest`) — directly covering the
  PRD's explicit instruction not to implement naive "wrong→easier,
  right→harder".
- **Grounded retrieval**: a question that lexically matches uploaded material
  retrieves it; an unrelated question retrieves nothing, which is what drives
  the Tutor's "insufficient evidence" path (`RetrievalServiceTest`).

> **Honest note on test execution**: this sandbox's network policy blocks
> `repo.maven.apache.org` (confirmed via a 403 on `mvn dependency:resolve`),
> so these tests were written correctly against the actual class/method
> signatures in this codebase and manually cross-checked (package/import
> resolution, DTO field order, Lombok accessor names, brace/paren balance)
> but **could not be executed by a live Maven build in this environment**.
> Run `mvn test` yourself on first checkout to confirm — see also
> [Known Limitations](#known-limitations).

## 5. The core learning loop

```
Register/Login → Create Space → Create Project → Upload PDF Material
   → (async) Extract text, chunk, detect concepts → Ready
   → Ask the Tutor (grounded answers + citations, or "insufficient evidence")
   → Take an Adaptive Quiz (MCQ + open-ended, evidence-based selection)
   → Mastery updates per concept → Growth trend → Recommendation
   → Project & Global Analytics, Admin Dashboard
```

## 6. Documentation index

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — architecture diagram and major decisions
- [`docs/AI_USAGE.md`](docs/AI_USAGE.md) — AI used to build this vs. AI used by the product
- [`docs/DEVELOPMENT_PROMPTS.md`](docs/DEVELOPMENT_PROMPTS.md) — prompts used during development
- [`docs/EVALUATION.md`](docs/EVALUATION.md) — how Tutor/retrieval/quiz/recommendation quality is evaluated
- [Known Limitations](#known-limitations) and [Future Improvements](#future-improvements) below

## Known Limitations

Documented honestly rather than hidden:

- **No live compile/test run.** This backend was generated in a sandboxed
  environment without access to Maven Central, so `mvn test`/`mvn package`
  were never executed here. The code was written carefully against real
  Spring Boot 3.2.x / Jakarta EE APIs and manually cross-checked for
  import/package/DTO consistency, but **you should run `mvn test` yourself
  on first checkout** before trusting it further.
- **Retrieval is lexical, not embedding-based.** `RetrievalService` scores
  material chunks by term overlap rather than vector similarity. This is
  dependency-free (no vector DB / embedding API required to run the
  prototype) but weaker on paraphrased questions that share little
  vocabulary with the source text. The seam to swap in a real
  embeddings-based retriever is `RetrievalService.retrieve(...)`.
- **Concept extraction is a heuristic**, not an LLM- or NLP-based extractor:
  it looks for capitalized multi-word phrases that recur across a document.
  It works reasonably on material that names its own terms (most textbooks
  and lecture notes do) but will miss concepts described only in lowercase
  prose.
- **No OCR.** PDF text is extracted via Apache PDFBox; scanned/image-only
  PDFs will fail processing with a clear error rather than silently
  producing nothing.
- **Background jobs run on an in-process thread pool**, not a durable queue
  (SQS/RabbitMQ/etc.). Jobs are lost if the process crashes mid-processing
  (though the Material simply stays `QUEUED`/`PROCESSING` and can be
  resubmitted — there's no automatic re-queue on restart in this prototype).
- **Cost/token accounting is approximate.** Token counts are estimated by
  character length (`length / 4`) rather than a real tokenizer, and cost is
  a rough per-model-class estimate — fine for relative observability, not
  for billing.
- **Single admin, no team/org model.** Admin is a role on the `User` entity,
  seeded once at startup; there's no invite flow or multi-tenant org layer.
- **No rate limiting** on AI-calling endpoints (Tutor ask, quiz generation).
- **AI evaluation is a small curated suite**, not a large regression corpus
  — see `docs/EVALUATION.md`.

## Future Improvements

- Swap lexical retrieval for embeddings (e.g. a local model or a vector DB)
  behind the same `RetrievalService` interface.
- Move background processing to a real queue (SQS/RabbitMQ) with a separate
  worker process, enabling horizontal scaling and durability across restarts.
- Add Flyway/Liquibase migrations instead of `ddl-auto: update`.
- Streaming Tutor responses (SSE/WebSocket) instead of request/response.
- A proper tokenizer-based cost/usage accounting and a tracing view (e.g.
  OpenTelemetry) in the Admin Dashboard.
- Automated regression evaluation that runs the curated eval suite in CI on
  every prompt/model/retrieval change.
- Real OCR pipeline (e.g. Tesseract or a hosted OCR API) for scanned PDFs.
