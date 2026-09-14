# Evaluation Approach

How each AI-dependent experience is evaluated, per PRD section 14 ("AI
Engineering, Observability & Evaluation").

## Philosophy

Every AI call in this product — mock or real — is logged through one seam
(`AiUsageRecorder` → `AiUsageLog` table), which is what makes "why was this
slow / which model / did it fail / how much did it cost" answerable from the
Admin Dashboard without any extra instrumentation work. Evaluation here is
built as a small, fast, curated suite rather than a large corpus — the right
scope for a 3–4 day prototype, with the explicit expectation (see Future
Improvements in the README) that this suite would grow and run in CI on every
prompt/model/retrieval change once the product had real usage to learn from.

## 1. Tutor — accuracy, groundedness, citation correctness, unsupported-question handling

**What's checked:**
- **Groundedness**: does the answer only use information present in the
  retrieved chunks, or does it add outside knowledge? Checked by comparing
  the answer's key claims against the evidence chunks it was given.
- **Citation correctness**: does every citation point to a chunk that was
  actually retrieved for this question (not fabricated), and does the cited
  material genuinely support the claim next to it?
- **Unsupported-question handling**: this is the single most important test
  in the suite, because the PRD calls it out as a core evaluation
  requirement. `RetrievalServiceTest.returnsNoEvidenceForUnrelatedQuery`
  verifies that a question with no lexical relationship to any uploaded
  material retrieves *zero* chunks — which is the exact signal
  `AiEngine.answerQuestion` uses to force `insufficientEvidence=true` rather
  than letting the model guess. This is tested at the retrieval layer
  (deterministic, fast, no model call needed) precisely because the
  downstream behavior (mock or real) is contractually driven by that
  signal — see `AiEngine.mockTutorAnswer`'s `!hasEvidence` branch and the
  real-mode system prompt's explicit instruction to set
  `insufficientEvidence=true` rather than fill gaps from outside knowledge.

**Curated cases** (exercised manually via the running app / would be
formalized as a fixture set with real model calls in CI):
- A question directly answerable from one chunk → expect a grounded answer
  with one accurate citation.
- A question spanning two uploaded documents → expect citations from both.
- A question about a topic never uploaded → expect
  `insufficientEvidence=true` and no fabricated citation.
- A follow-up question relying on conversation context ("what about that in
  practice?") → expect the recent-turns context (`TutorService.
  HISTORY_TURNS`) to resolve the reference correctly.

## 2. Retrieval — relevance and source quality

**Automated**: `RetrievalServiceTest` (two cases) — a matching query
retrieves the relevant chunk and ranks it first; an unrelated query retrieves
nothing (rather than returning low-relevance chunks as false evidence, which
would silently degrade groundedness). The `min-relevance-score` threshold
(`app.ai.retrieval.min-relevance-score`, default `0.08`) is the tunable knob
here — set too low and irrelevant chunks leak through as "evidence"; too high
and legitimate matches get dropped, surfacing as unnecessary
"insufficient evidence" responses. This is exactly the kind of regression the
PRD warns about ("changes to prompts, models, or retrieval can cause
regressions") and is why it's pulled into config rather than hardcoded.

**Known gap**: lexical retrieval can't judge relevance the way embeddings
can — a paraphrased question with little vocabulary overlap will
under-retrieve. This is disclosed in the README's Known Limitations, and is
the top item in Future Improvements.

## 3. Assessment — question quality, grading quality, structured output reliability, adaptive behavior

- **Structured output reliability**: `AiEngine.parseGeneratedQuestion` and
  `parseTutorAnswer` are defensive by construction — any JSON parse failure
  (malformed output, markdown fences the model added despite instructions,
  missing fields) falls back to the same rule-based mock generator used in
  mock mode, rather than surfacing a 500 to the learner. This means a bad
  model response degrades gracefully to "less personalized" rather than
  "broken."
- **Adaptive behavior**: `MasteryServiceTest` (three cases) is the core test
  here — it directly verifies the PRD's explicit anti-pattern warning is
  respected: a correct answer on a *harder* question moves mastery up more
  than a correct answer on an easy one; a wrong answer on an *easy* question
  is stronger negative evidence than a partial answer on a hard one; and
  evidence count / score bounds hold under repeated updates.
- **Question quality / grading quality**: evaluated qualitatively during
  development by running real quizzes against sample uploaded material and
  checking (a) MCQ distractors are plausible rather than obviously wrong, (b)
  open-ended grading feedback names specific missing concepts rather than
  returning a bare score (`AiEngine`'s system prompt for grading explicitly
  requires this — "Feedback must explain what the learner understood
  correctly and what is missing or wrong — never return only a number"). A
  larger, automated version of this would use a fixed set of (question,
  sample-answer, expected-score-range) fixtures and assert grading stays
  within range — a natural next CI addition once there's a corpus of real
  learner answers to calibrate against.

## 4. Recommendations — relevance, actionability, alignment with learner state

- **Relevance/alignment**: `RecommendationService.generateForProject` always
  grounds the recommendation in the *actual* weakest concept from
  `MasteryService.weakestConcepts`, never a generic message — checked by
  construction (the concept name and mastery percentage are injected directly
  into the prompt/template) rather than by a separate test, since this is
  closer to a data-plumbing guarantee than a judgment call.
- **Actionability**: both the real-model prompt and the mock-mode templates
  (`RecommendationService.templatedRecommendationText`) are written to name a
  concrete next action ("review the related material and complete another
  short assessment on X") rather than vague encouragement — this was a
  deliberate prompt-writing choice, checked by manual review of sample
  outputs in both mock and real mode.

## What a production version of this would add

- A real fixture-based eval harness (e.g. a JSON/YAML file of test cases per
  feature) run automatically in CI whenever `AiEngine`'s prompts, the model,
  or `RetrievalService`'s scoring changes — turning today's manual "run the
  app and read the outputs" check into a regression gate.
- Model-based grading of the Tutor's own answers (a second model call scoring
  groundedness/accuracy against the source chunks) for cases too nuanced for
  simple string/citation matching.
- A larger corpus of real (anonymized) learner questions and answers, once
  the product has actual usage, to replace hand-picked cases with
  representative ones.
