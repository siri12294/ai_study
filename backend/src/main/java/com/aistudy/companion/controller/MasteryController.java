package com.aistudy.companion.controller;

import com.aistudy.companion.dto.MasteryDtos.*;
import com.aistudy.companion.security.CurrentUser;
import com.aistudy.companion.service.MasteryService;
import com.aistudy.companion.service.ProjectService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}")
public class MasteryController {

    private final MasteryService masteryService;
    private final ProjectService projectService;
    private final CurrentUser currentUser;

    public MasteryController(MasteryService masteryService, ProjectService projectService, CurrentUser currentUser) {
        this.masteryService = masteryService;
        this.projectService = projectService;
        this.currentUser = currentUser;
    }

    @GetMapping("/mastery")
    public ResponseEntity<List<MasteryResponse>> mastery(@PathVariable String projectId) {
        projectService.getOwned(currentUser.get(), projectId);
        return ResponseEntity.ok(masteryService.listForProject(projectId));
    }

    @GetMapping("/growth")
    public ResponseEntity<List<GrowthResponse>> growth(@PathVariable String projectId) {
        projectService.getOwned(currentUser.get(), projectId);
        return ResponseEntity.ok(masteryService.growthForProject(projectId));
    }
}
