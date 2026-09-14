package com.aistudy.companion.dto;

import java.time.Instant;
import java.util.List;

public class AdminDtos {
    public record UserSummary(String id, String email, String displayName, String role, Instant createdAt,
                               int spaceCount, int projectCount) {}

    public record UserDetail(UserSummary user, List<ProjectDtos.ProjectResponse> projects,
                              List<ActivityDtos.ActivityResponse> recentActivity) {}

    public record SystemHealth(boolean databaseUp, long backgroundJobsQueued, long backgroundJobsFailed,
                                boolean aiConfigured, boolean aiMockMode, String aiModel) {}
}
