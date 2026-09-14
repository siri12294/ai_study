package com.aistudy.companion.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;

public class ProjectDtos {
    public record CreateProjectRequest(@NotBlank String name, String description, String goal) {}

    public record ProjectResponse(String id, String spaceId, String name, String description, String goal,
                                   Instant createdAt, Instant updatedAt) {}

    public record ProjectDashboardResponse(
            ProjectResponse project,
            double overallProgress,
            List<MasteryDtos.MasteryResponse> topConcepts,
            List<ActivityDtos.ActivityResponse> recentActivity,
            List<RecommendationDtos.RecommendationResponse> recommendations,
            Integer materialsReady,
            Integer materialsTotal
    ) {}
}
