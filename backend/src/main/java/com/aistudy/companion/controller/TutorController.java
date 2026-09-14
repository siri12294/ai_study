package com.aistudy.companion.controller;

import com.aistudy.companion.dto.TutorDtos.*;
import com.aistudy.companion.security.CurrentUser;
import com.aistudy.companion.service.ProjectService;
import com.aistudy.companion.service.TutorService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/tutor")
public class TutorController {

    private final TutorService tutorService;
    private final ProjectService projectService;
    private final CurrentUser currentUser;

    public TutorController(TutorService tutorService, ProjectService projectService, CurrentUser currentUser) {
        this.tutorService = tutorService;
        this.projectService = projectService;
        this.currentUser = currentUser;
    }

    @PostMapping("/ask")
    public ResponseEntity<TutorMessageResponse> ask(@PathVariable String projectId, @Valid @RequestBody AskRequest request) {
        var user = currentUser.get();
        var project = projectService.getOwned(user, projectId);
        var response = tutorService.ask(project, user, request.message());
        projectService.touch(projectId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/history")
    public ResponseEntity<List<TutorMessageResponse>> history(@PathVariable String projectId) {
        projectService.getOwned(currentUser.get(), projectId);
        return ResponseEntity.ok(tutorService.history(projectId));
    }
}
