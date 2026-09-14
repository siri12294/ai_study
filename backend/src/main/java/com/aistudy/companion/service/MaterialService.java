package com.aistudy.companion.service;

import com.aistudy.companion.dto.MaterialDtos.MaterialResponse;
import com.aistudy.companion.entity.Material;
import com.aistudy.companion.entity.Project;
import com.aistudy.companion.entity.User;
import com.aistudy.companion.exception.NotFoundException;
import com.aistudy.companion.repository.MaterialRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Handles Material upload: validation, storage, and persisting the initial
 * QUEUED row. The actual parsing/chunking/concept-extraction is delegated to
 * MaterialProcessingService (a separate bean - see its class comment for
 * why) so this request returns immediately without waiting on it, per the
 * "Asynchronous by Design" principle.
 */
@Service
public class MaterialService {

    private final MaterialRepository materialRepository;
    private final MaterialProcessingService processingService;
    private final ActivityService activityService;

    @Value("${app.uploads.dir}")
    private String uploadsDir;

    public MaterialService(MaterialRepository materialRepository, MaterialProcessingService processingService,
                            ActivityService activityService) {
        this.materialRepository = materialRepository;
        this.processingService = processingService;
        this.activityService = activityService;
    }

    @Transactional
    public Material upload(Project project, User uploader, MultipartFile file) {
        if (file.isEmpty()) throw new IllegalArgumentException("Uploaded file is empty.");
        String original = Objects.requireNonNullElse(file.getOriginalFilename(), "material.pdf");
        if (!original.toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException("Only PDF materials are supported in this prototype.");
        }
        try {
            Path dir = Path.of(uploadsDir, project.getId());
            Files.createDirectories(dir);
            String storedName = UUID.randomUUID() + "_" + original.replaceAll("[^a-zA-Z0-9._-]", "_");
            Path target = dir.resolve(storedName);
            file.transferTo(target.toFile());

            Material material = Material.builder()
                    .project(project).fileName(original).storedPath(target.toString())
                    .status(Material.Status.QUEUED)
                    .build();
            material = materialRepository.save(material);

            activityService.record(uploader, project, "MATERIAL_UPLOADED",
                    "{\"fileName\":\"" + original.replace("\"", "'") + "\"}", null);

            processingService.processAsync(material.getId());
            return material;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store uploaded file: " + e.getMessage(), e);
        }
    }

    public List<MaterialResponse> listForProject(String projectId) {
        return materialRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .map(m -> new MaterialResponse(m.getId(), m.getFileName(), m.getStatus().name(), m.getPageCount(),
                        m.getFailureReason(), m.getCreatedAt(), m.getReadyAt()))
                .toList();
    }

    public Material getOwned(Project project, String materialId) {
        Material m = materialRepository.findById(materialId).orElseThrow(() -> new NotFoundException("Material not found."));
        if (!m.getProject().getId().equals(project.getId())) throw new NotFoundException("Material not found.");
        return m;
    }
}
