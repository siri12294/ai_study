package com.aistudy.companion.dto;

import java.time.Instant;

public class RecommendationDtos {
    public record RecommendationResponse(String id, String text, String relatedConceptId, String status,
                                          Instant createdAt) {}
}
