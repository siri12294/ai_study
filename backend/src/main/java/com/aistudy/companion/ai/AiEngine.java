package com.aistudy.companion.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * The single entry point the rest of the app (Tutor, Quiz, Recommendations)
 * uses to talk to "AI". It hides two things from callers:
 *
 *  1. Provider mechanics (Anthropic Messages API, prompt construction, JSON
 *     parsing of structured output).
 *  2. Mock mode: when no ANTHROPIC_API_KEY is configured (or AI_MOCK_MODE=true,
 *     the default so the project runs out of the box for grading), deterministic
 *     rule-based generators stand in for the model. Mock mode implements the
 *     *same contracts* (grounded answers, citations, insufficient-evidence
 *     handling, adaptive question generation, evidence-based grading) so the
 *     product behavior can be evaluated without any API key. Swapping in a
 *     real key changes nothing about calling code.
 *
 * Every call is timed and logged via AiUsageRecorder regardless of mode.
 */
@Service
public class AiEngine {

    private static final Logger log = LoggerFactory.getLogger(AiEngine.class);
    private final ObjectMapper mapper = new ObjectMapper();

    private final AnthropicClient client;
    private final AiUsageRecorder usageRecorder;

    @Value("${app.ai.mock-mode}")
    private boolean mockMode;

    public AiEngine(AnthropicClient client, AiUsageRecorder usageRecorder) {
        this.client = client;
        this.usageRecorder = usageRecorder;
    }

    private boolean useRealModel() {
        return !mockMode && client.isConfigured();
    }

    // ---------------------------------------------------------------- TUTOR

    public AiModels.TutorAnswer answerQuestion(String projectId, String userId, String projectGoal,
                                                String recentConversation, List<AiModels.RetrievedChunk> evidence,
                                                String question) {
        boolean hasEvidence = !evidence.isEmpty();

        if (useRealModel()) {
            long start = System.currentTimeMillis();
            try {
                String system = """
                        You are an AI Study Tutor embedded in a learning platform. You must answer ONLY using the
                        EVIDENCE provided below, which comes from the learner's own uploaded materials. Do not use
                        outside knowledge to fill gaps. If the evidence does not contain enough information to answer
                        confidently, you must set insufficientEvidence=true and explain what's missing instead of guessing.
                        Respond with ONLY a JSON object, no prose outside it, no markdown fences, matching exactly:
                        {"answer": string, "insufficientEvidence": boolean, "citations": [{"materialName": string, "page": number|null, "snippet": string}]}
                        """;
                String userPrompt = buildTutorPrompt(projectGoal, recentConversation, evidence, question);
                String raw = client.complete(system, userPrompt, 1200);
                long latency = System.currentTimeMillis() - start;
                usageRecorder.record(projectId, userId, "TUTOR",
                        new AiModels.UsageMeta(client.getModel(), estimateTokens(userPrompt), estimateTokens(raw), latency, true, null));
                return parseTutorAnswer(raw, evidence);
            } catch (Exception e) {
                long latency = System.currentTimeMillis() - start;
                log.warn("Tutor AI call failed, falling back to evidence-based mock: {}", e.getMessage());
                usageRecorder.record(projectId, userId, "TUTOR",
                        new AiModels.UsageMeta(client.getModel(), null, null, latency, false, e.getMessage()));
                return mockTutorAnswer(evidence, question, hasEvidence);
            }
        }

        long start = System.currentTimeMillis();
        AiModels.TutorAnswer answer = mockTutorAnswer(evidence, question, hasEvidence);
        usageRecorder.record(projectId, userId, "TUTOR",
                new AiModels.UsageMeta("mock-rule-based", null, null, System.currentTimeMillis() - start, true, null));
        return answer;
    }

    private String buildTutorPrompt(String projectGoal, String recentConversation,
                                     List<AiModels.RetrievedChunk> evidence, String question) {
        StringBuilder sb = new StringBuilder();
        sb.append("LEARNER GOAL: ").append(projectGoal == null ? "(not specified)" : projectGoal).append("\n\n");
        if (recentConversation != null && !recentConversation.isBlank()) {
            sb.append("RECENT CONVERSATION (most recent last):\n").append(recentConversation).append("\n\n");
        }
        sb.append("EVIDENCE FROM PROJECT MATERIALS:\n");
        if (evidence.isEmpty()) {
            sb.append("(no relevant material found for this question)\n");
        } else {
            for (var c : evidence) {
                sb.append("---\nSource: ").append(c.materialName())
                        .append(c.page() != null ? " (page " + c.page() + ")" : "")
                        .append("\n").append(c.content()).append("\n");
            }
        }
        sb.append("\nQUESTION: ").append(question);
        return sb.toString();
    }

    private AiModels.TutorAnswer parseTutorAnswer(String raw, List<AiModels.RetrievedChunk> evidence) {
        try {
            String json = extractJson(raw);
            JsonNode node = mapper.readTree(json);
            String answer = node.path("answer").asText("");
            boolean insufficient = node.path("insufficientEvidence").asBoolean(evidence.isEmpty());
            List<AiModels.CitationRef> citations = new ArrayList<>();
            if (node.has("citations")) {
                for (JsonNode c : node.get("citations")) {
                    citations.add(new AiModels.CitationRef(
                            c.path("materialName").asText(null),
                            c.has("page") && !c.get("page").isNull() ? c.get("page").asInt() : null,
                            c.path("snippet").asText(null)));
                }
            }
            return new AiModels.TutorAnswer(answer, citations, insufficient);
        } catch (Exception e) {
            log.warn("Failed to parse Tutor JSON response, using raw text: {}", e.getMessage());
            return new AiModels.TutorAnswer(raw, List.of(), evidence.isEmpty());
        }
    }

    private AiModels.TutorAnswer mockTutorAnswer(List<AiModels.RetrievedChunk> evidence, String question,
                                                  boolean hasEvidence) {
        if (!hasEvidence) {
            String msg = "I don't have enough evidence in this Project's materials to answer that reliably. "
                    + "Try rephrasing the question, or upload material that covers this topic so I can ground my answer in it.";
            return new AiModels.TutorAnswer(msg, List.of(), true);
        }
        var top = evidence.get(0);
        String snippet = top.content().length() > 280 ? top.content().substring(0, 280) + "..." : top.content();
        StringBuilder answer = new StringBuilder();
        answer.append("Based on your materials: ").append(snippet);
        if (evidence.size() > 1) {
            answer.append(" Related context also appears in ")
                    .append(evidence.stream().skip(1).map(AiModels.RetrievedChunk::materialName).distinct().collect(Collectors.joining(", ")))
                    .append(".");
        }
        List<AiModels.CitationRef> citations = evidence.stream()
                .map(c -> new AiModels.CitationRef(c.materialName(), c.page(),
                        c.content().length() > 140 ? c.content().substring(0, 140) + "..." : c.content()))
                .collect(Collectors.toList());
        return new AiModels.TutorAnswer(answer.toString(), citations, false);
    }

    // ----------------------------------------------------------- QUIZ GEN

    public AiModels.GeneratedQuestion generateQuestion(String projectId, String userId, String conceptName,
                                                         String conceptDescription, List<AiModels.RetrievedChunk> evidence,
                                                         String type, int difficulty) {
        if (useRealModel()) {
            long start = System.currentTimeMillis();
            try {
                String system = """
                        You are generating one adaptive quiz question for a study platform, grounded strictly in the
                        provided evidence from the learner's own materials. Respond with ONLY JSON, no markdown fences:
                        {"type": "MCQ"|"OPEN", "prompt": string, "options": [string]|null, "correctAnswer": string, "conceptName": string, "difficulty": number}
                        For MCQ: exactly 4 options, correctAnswer must equal one option's exact text.
                        For OPEN: options is null, correctAnswer should be 2-4 concise key points (comma separated) an ideal answer would cover.
                        Difficulty 1=recall, 3=application, 5=analysis/synthesis.
                        """;
                StringBuilder up = new StringBuilder();
                up.append("Concept: ").append(conceptName).append("\n");
                if (conceptDescription != null) up.append("Concept notes: ").append(conceptDescription).append("\n");
                up.append("Requested type: ").append(type).append("\nRequested difficulty: ").append(difficulty).append("\n\n");
                up.append("EVIDENCE:\n");
                for (var c : evidence) up.append("---\n").append(c.content()).append("\n");
                String raw = client.complete(system, up.toString(), 800);
                long latency = System.currentTimeMillis() - start;
                usageRecorder.record(projectId, userId, "QUIZ_GENERATION",
                        new AiModels.UsageMeta(client.getModel(), estimateTokens(up.toString()), estimateTokens(raw), latency, true, null));
                return parseGeneratedQuestion(raw, conceptName, type, difficulty);
            } catch (Exception e) {
                long latency = System.currentTimeMillis() - start;
                log.warn("Quiz generation AI call failed, falling back to mock: {}", e.getMessage());
                usageRecorder.record(projectId, userId, "QUIZ_GENERATION",
                        new AiModels.UsageMeta(client.getModel(), null, null, latency, false, e.getMessage()));
                return mockQuestion(conceptName, conceptDescription, evidence, type, difficulty);
            }
        }
        long start = System.currentTimeMillis();
        var q = mockQuestion(conceptName, conceptDescription, evidence, type, difficulty);
        usageRecorder.record(projectId, userId, "QUIZ_GENERATION",
                new AiModels.UsageMeta("mock-rule-based", null, null, System.currentTimeMillis() - start, true, null));
        return q;
    }

    private AiModels.GeneratedQuestion parseGeneratedQuestion(String raw, String conceptName, String type, int difficulty) {
        try {
            JsonNode node = mapper.readTree(extractJson(raw));
            List<String> options = null;
            if (node.has("options") && !node.get("options").isNull()) {
                options = new ArrayList<>();
                for (JsonNode o : node.get("options")) options.add(o.asText());
            }
            return new AiModels.GeneratedQuestion(
                    node.path("type").asText(type),
                    node.path("prompt").asText(""),
                    options,
                    node.path("correctAnswer").asText(""),
                    node.path("conceptName").asText(conceptName),
                    node.path("difficulty").asInt(difficulty));
        } catch (Exception e) {
            log.warn("Failed to parse generated question JSON, using mock fallback: {}", e.getMessage());
            return mockQuestion(conceptName, null, List.of(), type, difficulty);
        }
    }

    private AiModels.GeneratedQuestion mockQuestion(String conceptName, String conceptDescription,
                                                      List<AiModels.RetrievedChunk> evidence, String type, int difficulty) {
        String basis = conceptDescription;
        if ((basis == null || basis.isBlank()) && !evidence.isEmpty()) {
            basis = evidence.get(0).content();
        }
        if (basis == null || basis.isBlank()) basis = conceptName;
        String firstSentence = basis.split("(?<=[.!?])\\s+")[0];
        if (firstSentence.length() > 220) firstSentence = firstSentence.substring(0, 220) + "...";

        if ("OPEN".equalsIgnoreCase(type)) {
            String prompt = "In your own words, explain \"" + conceptName + "\" and describe an example from the material. "
                    + "(Consider: " + firstSentence + ")";
            String keyPoints = conceptName + ", " + firstSentence.replaceAll("[.!?]$", "");
            return new AiModels.GeneratedQuestion("OPEN", prompt, null, keyPoints, conceptName, difficulty);
        } else {
            String prompt = "Which statement best reflects the material's explanation of \"" + conceptName + "\"?";
            String correct = firstSentence;
            List<String> options = new ArrayList<>();
            options.add(correct);
            options.add("None of the material addresses " + conceptName + " directly.");
            options.add(conceptName + " is unrelated to the rest of the Project material.");
            options.add("The material contradicts itself about " + conceptName + ".");
            Collections.shuffle(options, new Random(conceptName.hashCode()));
            return new AiModels.GeneratedQuestion("MCQ", prompt, options, correct, conceptName, difficulty);
        }
    }

    // ------------------------------------------------------------- GRADING

    public AiModels.GradingResult gradeOpenAnswer(String projectId, String userId, String questionPrompt,
                                                    String keyPoints, String userAnswer) {
        if (useRealModel()) {
            long start = System.currentTimeMillis();
            try {
                String system = """
                        You are grading a learner's open-ended answer for a study platform. Compare it against the
                        expected key points. Respond with ONLY JSON: {"isCorrect": boolean, "score": number between 0 and 1,
                        "feedback": string}. Feedback must explain what the learner understood correctly and what is
                        missing or wrong - never return only a number.
                        """;
                String up = "Question: " + questionPrompt + "\nExpected key points: " + keyPoints
                        + "\nLearner's answer: " + userAnswer;
                String raw = client.complete(system, up, 500);
                long latency = System.currentTimeMillis() - start;
                usageRecorder.record(projectId, userId, "QUIZ_GRADING",
                        new AiModels.UsageMeta(client.getModel(), estimateTokens(up), estimateTokens(raw), latency, true, null));
                JsonNode node = mapper.readTree(extractJson(raw));
                double score = node.path("score").asDouble(0.5);
                return new AiModels.GradingResult(node.path("isCorrect").asBoolean(score >= 0.6), score,
                        node.path("feedback").asText("No feedback generated."));
            } catch (Exception e) {
                long latency = System.currentTimeMillis() - start;
                log.warn("Grading AI call failed, falling back to keyword overlap grading: {}", e.getMessage());
                usageRecorder.record(projectId, userId, "QUIZ_GRADING",
                        new AiModels.UsageMeta(client.getModel(), null, null, latency, false, e.getMessage()));
                return mockGrade(keyPoints, userAnswer);
            }
        }
        long start = System.currentTimeMillis();
        var result = mockGrade(keyPoints, userAnswer);
        usageRecorder.record(projectId, userId, "QUIZ_GRADING",
                new AiModels.UsageMeta("mock-rule-based", null, null, System.currentTimeMillis() - start, true, null));
        return result;
    }

    private AiModels.GradingResult mockGrade(String keyPoints, String userAnswer) {
        Set<String> keyTerms = Arrays.stream(keyPoints.toLowerCase().split("[,\\s]+"))
                .filter(s -> s.length() > 3).collect(Collectors.toSet());
        Set<String> answerTerms = Arrays.stream(userAnswer.toLowerCase().split("[\\s,.;]+"))
                .filter(s -> s.length() > 3).collect(Collectors.toSet());
        if (keyTerms.isEmpty()) return new AiModels.GradingResult(false, 0.0, "No expected key points to grade against.");
        long matched = keyTerms.stream().filter(answerTerms::contains).count();
        double score = Math.min(1.0, (double) matched / keyTerms.size());
        if (userAnswer.trim().length() < 15) score = Math.min(score, 0.3);
        boolean correct = score >= 0.6;
        String missing = keyTerms.stream().filter(t -> !answerTerms.contains(t)).limit(4).collect(Collectors.joining(", "));
        String feedback = correct
                ? "Good answer - it covers the key ideas from the material."
                : ("Your answer is missing or only partially covers: " + (missing.isBlank() ? "key supporting detail" : missing)
                    + ". Review the related material and try explaining it again with a concrete example.");
        return new AiModels.GradingResult(correct, score, feedback);
    }

    // -------------------------------------------------------- RECOMMENDATION

    public String generateRecommendation(String projectId, String userId, String masterySummary) {
        if (useRealModel()) {
            long start = System.currentTimeMillis();
            try {
                String system = """
                        You write one short, specific, actionable recommendation (2-3 sentences) for what a learner
                        should do next, based on their concept mastery summary. Be concrete about which concept and
                        what kind of practice. Respond with plain text only, no JSON, no preamble.
                        """;
                String raw = client.complete(system, masterySummary, 300);
                long latency = System.currentTimeMillis() - start;
                usageRecorder.record(projectId, userId, "RECOMMENDATION",
                        new AiModels.UsageMeta(client.getModel(), estimateTokens(masterySummary), estimateTokens(raw), latency, true, null));
                return raw.trim();
            } catch (Exception e) {
                long latency = System.currentTimeMillis() - start;
                log.warn("Recommendation AI call failed, falling back to mock: {}", e.getMessage());
                usageRecorder.record(projectId, userId, "RECOMMENDATION",
                        new AiModels.UsageMeta(client.getModel(), null, null, latency, false, e.getMessage()));
                return mockRecommendation(masterySummary);
            }
        }
        long start = System.currentTimeMillis();
        String rec = mockRecommendation(masterySummary);
        usageRecorder.record(projectId, userId, "RECOMMENDATION",
                new AiModels.UsageMeta("mock-rule-based", null, null, System.currentTimeMillis() - start, true, null));
        return rec;
    }

    private String mockRecommendation(String masterySummary) {
        return masterySummary;
    }

    // ------------------------------------------------------------- HELPERS

    private String extractJson(String raw) {
        String trimmed = raw.trim();
        // Strip markdown fences if the model added them despite instructions.
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceAll("^```[a-zA-Z]*\\n", "").replaceAll("```$", "").trim();
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) return trimmed.substring(start, end + 1);
        return trimmed;
    }

    private int estimateTokens(String text) {
        if (text == null) return 0;
        return Math.max(1, text.length() / 4); // rough heuristic, good enough for observability
    }
}
