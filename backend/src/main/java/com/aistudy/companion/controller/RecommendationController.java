package com.aistudy.companion.controller;

import com.aistudy.companion.dto.RecommendationDtos.RecommendationResponse;
import com.aistudy.companion.security.CurrentUser;
import com.aistudy.companion.service.ProjectService;
import com.aistudy.companion.service.RecommendationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final ProjectService projectService;
    private final CurrentUser currentUser;

    public RecommendationController(RecommendationService recommendationService, ProjectService projectService, CurrentUser currentUser) {
        this.recommendationService = recommendationService;
        this.projectService = projectService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public ResponseEntity<List<RecommendationResponse>> list(@PathVariable String projectId) {
        projectService.getOwned(currentUser.get(), projectId);
        return ResponseEntity.ok(recommendationService.activeForProject(projectId));
    }

    @PostMapping("/{recommendationId}/dismiss")
    public ResponseEntity<Void> dismiss(@PathVariable String projectId, @PathVariable String recommendationId) {
        projectService.getOwned(currentUser.get(), projectId);
        recommendationService.dismiss(recommendationId);
        return ResponseEntity.noContent().build();
    }
}
