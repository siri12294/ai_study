package com.aistudy.companion.service;

import com.aistudy.companion.dto.ProjectDtos.*;
import com.aistudy.companion.entity.Material;
import com.aistudy.companion.entity.Project;
import com.aistudy.companion.entity.Space;
import com.aistudy.companion.entity.User;
import com.aistudy.companion.exception.ForbiddenException;
import com.aistudy.companion.exception.NotFoundException;
import com.aistudy.companion.repository.MaterialRepository;
import com.aistudy.companion.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final SpaceService spaceService;
    private final MaterialRepository materialRepository;
    private final MasteryService masteryService;
    private final ActivityService activityService;
    private final RecommendationService recommendationService;

    public ProjectService(ProjectRepository projectRepository, SpaceService spaceService,
                           MaterialRepository materialRepository, MasteryService masteryService,
                           ActivityService activityService, RecommendationService recommendationService) {
        this.projectRepository = projectRepository;
        this.spaceService = spaceService;
        this.materialRepository = materialRepository;
        this.masteryService = masteryService;
        this.activityService = activityService;
        this.recommendationService = recommendationService;
    }

    @Transactional
    public ProjectResponse create(User user, String spaceId, CreateProjectRequest request) {
        Space space = spaceService.getOwned(user, spaceId);
        Project project = Project.builder()
                .space(space).user(user)
                .name(request.name()).description(request.description()).goal(request.goal())
                .build();
        project = projectRepository.save(project);
        activityService.record(user, project, "PROJECT_CREATED", "{\"name\":\"" + escape(request.name()) + "\"}", null);
        return toResponse(project);
    }

    public List<ProjectResponse> listForSpace(User user, String spaceId) {
        spaceService.getOwned(user, spaceId);
        return projectRepository.findBySpaceIdOrderByUpdatedAtDesc(spaceId).stream().map(this::toResponse).toList();
    }

    public List<ProjectResponse> listForUser(User user) {
        return projectRepository.findByUserIdOrderByUpdatedAtDesc(user.getId()).stream().map(this::toResponse).toList();
    }

    public Project getOwned(User user, String projectId) {
        Project project = projectRepository.findById(projectId).orElseThrow(() -> new NotFoundException("Project not found."));
        if (!project.getUser().getId().equals(user.getId()) && user.getRole() != User.Role.ADMIN) {
            throw new ForbiddenException("You do not have access to this Project.");
        }
        return project;
    }

    @Transactional
    public ProjectDashboardResponse dashboard(User user, String projectId) {
        Project project = getOwned(user, projectId);
        List<Material> materials = materialRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
        long ready = materials.stream().filter(m -> m.getStatus() == Material.Status.READY).count();

        var recs = recommendationService.activeForProject(projectId);
        if (recs.isEmpty() && !materials.isEmpty()) {
            recommendationService.generateForProject(project, user.getId());
            recs = recommendationService.activeForProject(projectId);
        }

        return new ProjectDashboardResponse(
                toResponse(project),
                masteryService.overallProgress(projectId),
                masteryService.listForProject(projectId).stream().limit(5).toList(),
                activityService.recentForProject(projectId, 10),
                recs,
                (int) ready,
                materials.size()
        );
    }

    @Transactional
    public void touch(String projectId) {
        projectRepository.findById(projectId).ifPresent(p -> {
            p.setUpdatedAt(Instant.now());
            projectRepository.save(p);
        });
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("\"", "'");
    }

    private ProjectResponse toResponse(Project p) {
        return new ProjectResponse(p.getId(), p.getSpace().getId(), p.getName(), p.getDescription(), p.getGoal(),
                p.getCreatedAt(), p.getUpdatedAt());
    }
}
