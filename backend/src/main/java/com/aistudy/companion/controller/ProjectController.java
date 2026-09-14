package com.aistudy.companion.controller;

import com.aistudy.companion.dto.ProjectDtos.*;
import com.aistudy.companion.security.CurrentUser;
import com.aistudy.companion.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class ProjectController {

    private final ProjectService projectService;
    private final CurrentUser currentUser;

    public ProjectController(ProjectService projectService, CurrentUser currentUser) {
        this.projectService = projectService;
        this.currentUser = currentUser;
    }

    @PostMapping("/api/spaces/{spaceId}/projects")
    public ResponseEntity<ProjectResponse> create(@PathVariable String spaceId, @Valid @RequestBody CreateProjectRequest request) {
        return ResponseEntity.ok(projectService.create(currentUser.get(), spaceId, request));
    }

    @GetMapping("/api/spaces/{spaceId}/projects")
    public ResponseEntity<List<ProjectResponse>> listForSpace(@PathVariable String spaceId) {
        return ResponseEntity.ok(projectService.listForSpace(currentUser.get(), spaceId));
    }

    @GetMapping("/api/projects")
    public ResponseEntity<List<ProjectResponse>> listAll() {
        return ResponseEntity.ok(projectService.listForUser(currentUser.get()));
    }

    @GetMapping("/api/projects/{projectId}/dashboard")
    public ResponseEntity<ProjectDashboardResponse> dashboard(@PathVariable String projectId) {
        return ResponseEntity.ok(projectService.dashboard(currentUser.get(), projectId));
    }
}
