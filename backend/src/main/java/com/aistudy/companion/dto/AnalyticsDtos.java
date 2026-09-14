package com.aistudy.companion.dto;

import java.util.List;
import java.util.Map;

public class AnalyticsDtos {
    public record ProjectAnalyticsResponse(
            long tutorMessages,
            long quizzesCompleted,
            long questionsAnswered,
            double averageQuizScore,
            List<MasteryDtos.MasteryResponse> masteryByConcept,
            Map<String, Long> activityByType
    ) {}

    public record GlobalAnalyticsResponse(
            long totalUsers,
            long totalSpaces,
            long totalProjects,
            long totalMaterials,
            long totalTutorMessages,
            long totalQuizzesCompleted,
            Map<String, Long> activityByType,
            List<AiUsageSummary> aiUsageByFeature
    ) {}

    public record AiUsageSummary(String feature, long callCount, double avgLatencyMs, double totalEstimatedCostUsd,
                                  double successRate) {}
}
