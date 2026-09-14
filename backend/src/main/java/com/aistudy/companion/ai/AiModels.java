package com.aistudy.companion.ai;

import java.util.List;

/** Plain internal models used across the AI layer, independent of any provider's wire format. */
public class AiModels {

    public record RetrievedChunk(String chunkId, String materialId, String materialName, Integer page,
                                  String content, double relevanceScore) {}

    public record CitationRef(String materialName, Integer page, String snippet) {}

    public record TutorAnswer(String answer, List<CitationRef> citations, boolean insufficientEvidence) {}

    public record GeneratedQuestion(String type, String prompt, List<String> options, String correctAnswer,
                                     String conceptName, int difficulty) {}

    public record GradingResult(boolean isCorrect, double score, String feedback) {}

    public record UsageMeta(String model, Integer tokensIn, Integer tokensOut, long latencyMs, boolean success,
                             String errorMessage) {}
}
