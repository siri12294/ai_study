# Architecture

## Diagram

```
                                   ┌─────────────────────────┐
                                   │   React 18 + Vite SPA    │
                                   │  (auth, spaces/projects, │
                                   │   materials, tutor,      │
                                   │   quiz, growth, admin)   │
                                   └────────────┬─────────────┘
                                                │ HTTPS / JSON (JWT bearer)
                                   ┌────────────▼─────────────┐
                                   │   Spring Boot REST API   │
                                   │  (Controllers, per-      │
                                   │   Project auth checks)   │
                                   └────────────┬─────────────┘
                                                │
        ┌───────────────────┬──────────────────┼──────────────────┬───────────────────┐
        │                   │                  │                  │                   │
┌───────▼───────┐  ┌────────▼────────┐ ┌───────▼────────┐ ┌───────▼────────┐ ┌────────▼────────┐
│  Auth /        │  │  Learning        │ │  AI Layer       │ │  Assessment     │ │  Analytics /     │
│  Space/Project │  │  (Mastery,       │ │  (AiEngine,     │ │  (Quiz          │ │  Admin           │
│  Service       │  │  Growth, Rec.)   │ │  RetrievalSvc,  │ │  Service)       │ │  (Analytics,     │
│                │  │                  │ │  AnthropicClient)│ │                │ │  AdminService)   │
└───────┬───────┘  └────────┬────────┘ └───────┬────────┘ └───────┬────────┘ └────────┬────────┘
        │                   │                  │                  │                   │
        └───────────────────┴──────────────────┼──────────────────┴───────────────────┘
                                                │
                                   ┌────────────▼─────────────┐
                                   │   Spring Data JPA         │
                                   │   (H2 dev / Postgres prod)│
                                   └────────────┬─────────────┘
                                                │
                       ┌────────────────────────┼─────────────────────────┐
                       │                        │                         │
              ┌────────▼────────┐    ┌──────────▼──────────┐   ┌──────────▼──────────┐
              │ Document storage │    │ Background executor  │   │ AI usage log table   │
              │ (local disk;     │    │ (ThreadPoolTaskExecutor,│  │ (latency, tokens,    │
              │  S3-ready path)  │    │  MaterialProcessingSvc) │  │  cost, success)      │
              └──────────────────┘    └──────────────────────┘   └──────────────────────┘
                                                │
                                   ┌────────────▼─────────────┐
                                   │   Anthropic Messages API   │
                                   │   (or mock mode - see AI   │
                                   │   Engine below)            │
                                   └───────────────────────────┘
```

## Major decisions

### 1. Monolith, not microservices
A single Spring Boot application with clearly separated service classes
(one per capability: Auth, Space, Project, Material, MaterialProcessing,
Tutor, Quiz, Mastery, Recommendation, Activity, Analytics, Admin) rather than
a network of services. For a 3–4 day prototype, network boundaries between
services would add deployment and debugging overhead without a corresponding
benefit — there's one team, one deploy, and no component that needs to scale
independently yet. The service boundaries are still clean (each is a
narrowly-scoped Spring `@Service` with a single responsibility), so splitting
any of them out later is a refactor, not a rewrite.

### 2. AI Engine as a single seam (`AiEngine`)
Every AI-dependent feature (Tutor, quiz generation, quiz grading,
recommendations) goes through one class, `AiEngine`, which itself decides
whether to call the real Anthropic API (`AnthropicClient`) or fall back to a
deterministic, rule-based **mock mode**. Two consequences:

- **The whole product runs and is fully demonstrable with zero API key
  configured.** `AI_MOCK_MODE=true` is the default. Mock mode doesn't fake
  success — it implements the *same contracts* the real model would: grounded
  answers, citations, "insufficient evidence" handling, adaptive question
  generation grounded in retrieved chunks, and evidence-based grading. This
  matters for evaluating the product's behavior/architecture independent of
  whether a reviewer wants to configure billing.
- **Swapping providers is contained.** `AnthropicClient` is a ~60-line wrapper
  around one endpoint. A different provider (OpenAI, Bedrock) means writing
  one new class with a `complete(system, user, maxTokens)` method and wiring
  it into `AiEngine` — no changes to Tutor/Quiz/Recommendation logic.

Every call through `AiEngine`, mock or real, is timed and logged via
`AiUsageRecorder` into an `AiUsageLog` table, which is what the Admin
Dashboard's "AI usage by feature" view reads from — this was a design choice
to make "why was this slow / how much did it cost / which model / did it
fail" answerable from day one rather than bolted on later.

### 3. Retrieval: lexical overlap, not embeddings
`RetrievalService` scores material chunks against a query using term-overlap
scoring, not vector embeddings. This was a conscious trade-off for a
dependency-free prototype: no vector database, no embedding API calls, and
retrieval quality is inspectable/debuggable by reading the scoring code
directly. The trade-off is real — it's weaker on paraphrased questions that
share little vocabulary with the source text — and is called out explicitly
in [Known Limitations](../README.md#known-limitations). The interface
(`retrieve(projectId, query) -> List<RetrievedChunk>`) is the seam where a
real embeddings-based retriever would plug in without touching Tutor or Quiz
code.

### 4. Async processing: background thread pool, own bean, no self-invocation
Material processing (PDF text extraction, chunking, concept detection) runs
on a dedicated `ThreadPoolTaskExecutor` (see `AsyncConfig`) via
`MaterialProcessingService.processAsync(...)`, so `POST /materials` returns
immediately.

Worth calling out as a specific decision: `MaterialProcessingService` is a
**separate bean** from `MaterialService` (which handles upload/storage/
listing). Spring's `@Async` (and `@Transactional`) only takes effect on
calls that go through the Spring AOP proxy — a method calling another
`@Async` method *in the same class* bypasses the proxy and runs
synchronously, silently defeating "asynchronous by design". Splitting upload
(`MaterialService`) from processing (`MaterialProcessingService`) means the
call crossing the async boundary is a normal cross-bean call, so it's
genuinely non-blocking. Retries for a single processing job are a plain loop
inside `processAsync` rather than recursive self-calls, for the same reason
and because a retry doesn't need to hop threads again.

Idempotency: reprocessing a `READY` material is a no-op; a retried attempt
deletes any partial chunks before re-inserting, so a crash mid-attempt can't
leave duplicate chunks behind.

### 5. Persistent but relevant context, not full history replay
The Tutor doesn't send the full conversation history on every request. It
sends the last few turns (`TutorService.HISTORY_TURNS = 6`) plus whatever
material chunks the current question retrieves, plus the Project's stated
goal. This is a direct implementation of the PRD's "Persistent but Relevant
Context" principle — the system remembers *useful* things (goal, recent
turns, retrieved evidence) without replaying or re-embedding the entire
conversation on every call.

### 6. Adaptive quiz selection: evidence-weighted, not naive
Two rules work together in `QuizService`/`MasteryService`, both directly
targeting the PRD's explicit instruction *not* to implement "wrong → easier,
right → harder":

- **Concept selection** (`QuizService.selectConcept`) picks whichever concept
  currently has the lowest mastery score, with a small penalty for evidence
  volume (so a concept isn't tested forever once it has plenty of data) and a
  penalty for having just been tested in the same attempt (avoid immediate
  repeats). This means a *correct* answer on a weak concept can still be
  followed by another question on that same concept if it's still the
  weakest one — not automatically bumped to "harder, next concept".
- **Mastery update** (`MasteryService.applyEvidence`) moves the score toward
  the observed evidence, weighted by the *target concept's* difficulty band:
  a correct answer on a hard question moves mastery up more than a correct
  answer on an easy one; a wrong answer on an easy question is stronger
  negative evidence than struggling on a hard one. This is covered directly
  by `MasteryServiceTest`.

### 7. Security / data isolation
Every Project-scoped service method takes the authenticated `User` and calls
`get*Owned(user, id)` before doing anything else, which throws `403` if the
resource belongs to someone else (see `SpaceService.getOwned`,
`ProjectService.getOwned`, `MaterialService.getOwned`,
`QuizService.getOwnedAttempt`). This is enforced in the service layer, not
just the controller layer, so it can't be accidentally bypassed by a new
endpoint that forgets to check. `/api/admin/**` is additionally gated by
`ROLE_ADMIN` in `SecurityConfig` at the filter-chain level, independent of
anything a controller does. AI-generated structured data (quiz questions,
grading results) is parsed defensively (`AiEngine.extractJson` +
try/catch-per-field with fallbacks) before it's persisted, rather than
trusted as-is — see `AiEngine.parseGeneratedQuestion` /
`parseTutorAnswer` for the pattern.

### 8. Database
H2 (file-backed) by default for zero-setup local running; Postgres is a
five-environment-variable swap (see main README). `ddl-auto: update` is a
prototype-appropriate simplification — see Future Improvements for the
migration-tooling upgrade path.
