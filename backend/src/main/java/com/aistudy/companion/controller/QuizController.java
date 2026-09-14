package com.aistudy.companion.controller;

import com.aistudy.companion.dto.QuizDtos.*;
import com.aistudy.companion.security.CurrentUser;
import com.aistudy.companion.service.ProjectService;
import com.aistudy.companion.service.QuizService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}/quiz")
public class QuizController {

    private final QuizService quizService;
    private final ProjectService projectService;
    private final CurrentUser currentUser;

    public QuizController(QuizService quizService, ProjectService projectService, CurrentUser currentUser) {
        this.quizService = quizService;
        this.projectService = projectService;
        this.currentUser = currentUser;
    }

    @PostMapping("/start")
    public ResponseEntity<StartQuizResponse> start(@PathVariable String projectId, @RequestBody(required = false) StartQuizRequest request) {
        var user = currentUser.get();
        var project = projectService.getOwned(user, projectId);
        var body = request == null ? new StartQuizRequest(null) : request;
        var response = quizService.start(project, user, body);
        projectService.touch(projectId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{attemptId}/questions/{questionId}/answer")
    public ResponseEntity<AnswerResultResponse> answer(@PathVariable String projectId, @PathVariable String attemptId,
                                                         @PathVariable String questionId,
                                                         @Valid @RequestBody SubmitAnswerRequest request) {
        var user = currentUser.get();
        var project = projectService.getOwned(user, projectId);
        var response = quizService.submitAnswer(project, user, attemptId, questionId, request);
        projectService.touch(projectId);
        return ResponseEntity.ok(response);
    }
}
