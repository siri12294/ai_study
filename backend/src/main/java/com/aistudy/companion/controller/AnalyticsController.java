package com.aistudy.companion.controller;

import com.aistudy.companion.dto.AnalyticsDtos.ProjectAnalyticsResponse;
import com.aistudy.companion.security.CurrentUser;
import com.aistudy.companion.service.AnalyticsService;
import com.aistudy.companion.service.ProjectService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final ProjectService projectService;
    private final CurrentUser currentUser;

    public AnalyticsController(AnalyticsService analyticsService, ProjectService projectService, CurrentUser currentUser) {
        this.analyticsService = analyticsService;
        this.projectService = projectService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public ResponseEntity<ProjectAnalyticsResponse> forProject(@PathVariable String projectId) {
        projectService.getOwned(currentUser.get(), projectId);
        return ResponseEntity.ok(analyticsService.forProject(projectId));
    }
}
