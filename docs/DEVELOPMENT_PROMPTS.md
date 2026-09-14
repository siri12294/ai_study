# Development Prompts

This project was built end-to-end in a single agentic session with Claude, in
response to two prompts from the user (paraphrased structure preserved,
organized by area below per the PRD's request). The full PRD document
(`Project_Requirements.pdf`, "AI Study Companion — Product Requirements
Document v3.0") was provided as the primary specification.

## Initiating prompts

1. *"explain me [the PRD]"* — Claude read and summarized the PRD before any
   code was written.
2. *"can u make me the full project and keep it in a zip file using
   springboot and react"* — the build request that produced this repository.
   Claude's response set expectations up front (a working scaffold covering
   the full core loop, not a claim of a polished commercial product) before
   proceeding.
3. *"Continue"* — after the first response ran out of turn budget partway
   through the backend, this continued the same build session.

## Architecture / planning

- Internal planning (not a separate user prompt, but the first substantive
  step): decompose the PRD into entities, services, and controllers; decide
  monolith vs. microservices (monolith, see `docs/ARCHITECTURE.md` §1); decide
  the AI provider seam and mock-mode strategy before writing any AI code, so
  every feature could be built and demoed without requiring API billing.

## Backend

- Systematic file-by-file generation following a fixed order: entities →
  repositories → security (JWT) → config (CORS, async executor, WebClient) →
  DTOs → AI layer (`AiModels`, `AnthropicClient`, `RetrievalService`,
  `AiUsageRecorder`, `AiEngine`) → exception handling → services → controllers
  → data seeding (default admin) → tests.
- Explicit self-review prompts run against the generated code (not
  externally supplied, self-directed): "does every DTO record's field order
  match every call site", "does every Lombok getter/setter name match usage
  given primitive vs. boxed Boolean fields", "is there a self-invocation bug
  anywhere `@Async` or `@Transactional` methods call each other within the
  same class". The last one found a real bug — see `docs/AI_USAGE.md` and
  `docs/ARCHITECTURE.md` §4 — and was fixed by splitting
  `MaterialProcessingService` out of `MaterialService`.
- Verification prompt: attempt `mvn compile` / `mvn dependency:resolve` in
  the sandbox to check for real compile errors. Result: blocked by a `403
  Forbidden` from the sandbox's egress proxy for `repo.maven.apache.org`
  (Maven Central), not a code issue — disclosed in the README rather than
  hidden. Follow-up: wrote and ran custom Python static-analysis scripts
  against every `.java` file to check brace/paren balance, package-declaration
  vs. file-path consistency, and that internal `com.aistudy.companion.*`
  imports resolve to types that actually exist in the codebase.

## Frontend

- Read the environment's `frontend-design` skill guidance before writing any
  UI code, and deliberately chose a "field notebook" visual concept (deep
  forest-green chrome, warm paper content, muted brass/gold accent, Source
  Serif 4 for headings + Inter for UI text) specifically to avoid the
  documented AI-generated-design defaults (warm cream + terracotta; dark +
  neon; generic SaaS card kit) for a subject this document itself identifies
  as "less like a chatbot and more like a real learning partner."
- Built page-by-page following the core loop order: auth → spaces →
  space detail/projects → project layout (sidebar) → dashboard → materials
  (with upload + status polling) → Tutor chat → adaptive quiz (multi-step
  state machine: start → answer → feedback → next/complete) → growth/mastery
  (with a hand-rolled inline SVG sparkline, no charting dependency) →
  analytics → admin dashboard.
- Verification: ran `npm install && npm run build` in the sandbox. This
  succeeded (`vite build` completed, `dist/` produced) — unlike the backend,
  npm's registry was reachable from this sandbox, so the frontend's build
  correctness is verified, not just reviewed.

## Documentation

- Final prompts (self-directed, matching the PRD's Final Submission
  Requirements checklist) produced this README, `ARCHITECTURE.md`,
  `AI_USAGE.md` (this file's sibling), `DEVELOPMENT_PROMPTS.md` (this file),
  and `EVALUATION.md`, cross-checked against PRD section 20 item by item to
  make sure every required deliverable had a corresponding artifact in the
  repository.
