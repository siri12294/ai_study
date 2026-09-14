package com.aistudy.companion.controller;

import com.aistudy.companion.dto.MaterialDtos.MaterialResponse;
import com.aistudy.companion.entity.Material;
import com.aistudy.companion.security.CurrentUser;
import com.aistudy.companion.service.MaterialService;
import com.aistudy.companion.service.ProjectService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/materials")
public class MaterialController {

    private final MaterialService materialService;
    private final ProjectService projectService;
    private final CurrentUser currentUser;

    public MaterialController(MaterialService materialService, ProjectService projectService, CurrentUser currentUser) {
        this.materialService = materialService;
        this.projectService = projectService;
        this.currentUser = currentUser;
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<MaterialResponse> upload(@PathVariable String projectId, @RequestParam("file") MultipartFile file) {
        var user = currentUser.get();
        var project = projectService.getOwned(user, projectId);
        Material material = materialService.upload(project, user, file);
        return ResponseEntity.ok(new MaterialResponse(material.getId(), material.getFileName(),
                material.getStatus().name(), material.getPageCount(), material.getFailureReason(),
                material.getCreatedAt(), material.getReadyAt()));
    }

    @GetMapping
    public ResponseEntity<List<MaterialResponse>> list(@PathVariable String projectId) {
        projectService.getOwned(currentUser.get(), projectId);
        return ResponseEntity.ok(materialService.listForProject(projectId));
    }
}
