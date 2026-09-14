package com.aistudy.companion.service;

import com.aistudy.companion.ai.AnthropicClient;
import com.aistudy.companion.dto.AdminDtos.*;
import com.aistudy.companion.dto.ProjectDtos.ProjectResponse;
import com.aistudy.companion.entity.Material;

import com.aistudy.companion.entity.User;
import com.aistudy.companion.exception.NotFoundException;
import com.aistudy.companion.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/** Backs the Admin Dashboard: platform-wide visibility into users, activity, and system health. */
@Service
public class AdminService {

    private final UserRepository userRepository;
    private final SpaceRepository spaceRepository;
    private final ProjectRepository projectRepository;
    private final MaterialRepository materialRepository;
    private final ActivityService activityService;
    private final AnthropicClient anthropicClient;

    @Value("${app.ai.mock-mode}")
    private boolean mockMode;

    public AdminService(UserRepository userRepository, SpaceRepository spaceRepository, ProjectRepository projectRepository,
                         MaterialRepository materialRepository, ActivityService activityService, AnthropicClient anthropicClient) {
        this.userRepository = userRepository;
        this.spaceRepository = spaceRepository;
        this.projectRepository = projectRepository;
        this.materialRepository = materialRepository;
        this.activityService = activityService;
        this.anthropicClient = anthropicClient;
    }

    public List<UserSummary> listUsers() {
        return userRepository.findAll().stream().map(u -> new UserSummary(
                u.getId(), u.getEmail(), u.getDisplayName(), u.getRole().name(), u.getCreatedAt(),
                spaceRepository.findByUserIdOrderByCreatedAtDesc(u.getId()).size(),
                projectRepository.findByUserIdOrderByUpdatedAtDesc(u.getId()).size()
        )).toList();
    }

    public UserDetail userDetail(String userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found."));
        List<ProjectResponse> projects = projectRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(p -> new ProjectResponse(p.getId(), p.getSpace().getId(), p.getName(), p.getDescription(),
                        p.getGoal(), p.getCreatedAt(), p.getUpdatedAt()))
                .toList();
        UserSummary summary = new UserSummary(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole().name(),
                user.getCreatedAt(), spaceRepository.findByUserIdOrderByCreatedAtDesc(userId).size(), projects.size());
        return new UserDetail(summary, projects, activityService.recentForUser(userId, 25));
    }

    public SystemHealth systemHealth() {
        long queued = materialRepository.findAll().stream()
                .filter(m -> m.getStatus() == Material.Status.QUEUED || m.getStatus() == Material.Status.PROCESSING).count();
        long failed = materialRepository.findAll().stream().filter(m -> m.getStatus() == Material.Status.FAILED).count();
        boolean dbUp = true;
        try {
            userRepository.count();
        } catch (Exception e) {
            dbUp = false;
        }
        return new SystemHealth(dbUp, queued, failed, anthropicClient.isConfigured(), mockMode, anthropicClient.getModel());
    }
}
