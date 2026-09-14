# AI Usage

Two distinct categories, as requested by the PRD: AI used *to build* this
product, and AI used *by* the product itself.

## AI used to build this product

This entire codebase (backend, frontend, and this documentation) was written
by **Claude** (Anthropic), operating as an agentic coding assistant with a
sandboxed Linux environment (bash, file read/write, and — critically for
verification — `npm`/Node access; Maven Central was network-blocked in this
particular sandbox, which is disclosed explicitly in the README and below
rather than hidden).

What that looked like in practice:
- Writing all Java source (entities, repositories, security, the AI
  orchestration layer, services, controllers, DTOs, exception handling) from
  the PRD's requirements.
- Writing the React/Vite frontend (routing, API client, auth context, all
  pages/components) and its design tokens.
- Running `npm install && npm run build` inside the sandbox to verify the
  frontend actually compiles — it does (see the build log referenced in
  `docs/DEVELOPMENT_PROMPTS.md`).
- Attempting the equivalent for the backend (`mvn compile`/`mvn test`), which
  failed only because the sandbox's egress proxy returned `403 Forbidden` for
  `repo.maven.apache.org` — not because of a code defect. This is disclosed
  plainly in the README's Known Limitations rather than presented as a clean
  green build.
- Because live compilation wasn't possible, the backend was additionally
  checked with a set of custom static-analysis passes run in the sandbox: a
  brace/paren-balance and package/filename consistency check across every
  `.java` file, and a cross-reference of internal imports against defined
  types. Both passes are reproducible (see `docs/DEVELOPMENT_PROMPTS.md`).
  These catch a meaningful class of errors but are **not a substitute for
  actually running `mvn test`** — please do that on first checkout.
- One real bug was found and fixed during this process: `MaterialService`
  originally tried to kick off asynchronous processing by calling `this.
  processAsync(...)` from within the same class, which is a known Spring AOP
  limitation (self-invocation bypasses the `@Async` proxy and runs
  synchronously instead, silently defeating "asynchronous by design"). The
  fix was to split upload/storage (`MaterialService`) from the actual
  processing pipeline (`MaterialProcessingService`) into two beans — see
  `docs/ARCHITECTURE.md` section 4.

No other coding assistants, scaffolding generators, or design tools were used
in this project.

## AI used by the product

| Feature | What it does | Where |
|---|---|---|
| **AI Tutor** | Answers learner questions grounded in retrieved Project material; returns structured JSON (answer, citations, insufficient-evidence flag) | `AiEngine.answerQuestion` |
| **Quiz generation** | Produces one MCQ or open-ended question per call, grounded in retrieved evidence for the target concept, at a specified difficulty | `AiEngine.generateQuestion` |
| **Open-answer grading** | Compares a learner's free-text answer against expected key points; returns correctness, a 0–1 score, and specific feedback (what was understood vs. missing) | `AiEngine.gradeOpenAnswer` |
| **Recommendations** | Turns the weakest-concept mastery summary into one concrete, specific next action | `AiEngine.generateRecommendation` |

**Model**: Claude (`claude-sonnet-4-6` by default, configurable via
`ANTHROPIC_MODEL`), called through the plain Anthropic Messages API
(`AnthropicClient`) — no framework/SDK dependency beyond a `WebClient` HTTP
call, deliberately, to keep the AI layer's actual mechanics inspectable in
one file.

**Mock mode**: `AI_MOCK_MODE=true` by default. Every one of the four features
above has a deterministic, rule-based fallback that implements the same
contract (see `AiEngine`'s `mock*` methods) so the product is fully
demonstrable — including the "insufficient evidence" and adaptive-difficulty
behaviors — without any API key. This was a product decision as much as an
engineering one: a reviewer shouldn't need billing configured to see the
actual behavior being evaluated.

**Structured output handling**: the Tutor and quiz-generation prompts ask the
model to return JSON only; responses are parsed defensively
(`AiEngine.extractJson`, per-field fallbacks) and never trusted as
executable or as direct database input — see `docs/ARCHITECTURE.md` section
7 ("Safe AI Interaction").
