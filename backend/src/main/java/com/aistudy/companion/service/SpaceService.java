package com.aistudy.companion.service;

import com.aistudy.companion.dto.SpaceDtos.*;
import com.aistudy.companion.entity.Space;
import com.aistudy.companion.entity.User;
import com.aistudy.companion.exception.ForbiddenException;
import com.aistudy.companion.exception.NotFoundException;
import com.aistudy.companion.repository.ProjectRepository;
import com.aistudy.companion.repository.SpaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SpaceService {

    private final SpaceRepository spaceRepository;
    private final ProjectRepository projectRepository;

    public SpaceService(SpaceRepository spaceRepository, ProjectRepository projectRepository) {
        this.spaceRepository = spaceRepository;
        this.projectRepository = projectRepository;
    }

    @Transactional
    public SpaceResponse create(User user, CreateSpaceRequest request) {
        Space space = Space.builder()
                .user(user)
                .name(request.name())
                .description(request.description())
                .colorTheme(request.colorTheme())
                .build();
        space = spaceRepository.save(space);
        return toResponse(space, 0);
    }

    public List<SpaceResponse> listForUser(User user) {
        return spaceRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(s -> toResponse(s, projectRepository.findBySpaceIdOrderByUpdatedAtDesc(s.getId()).size()))
                .toList();
    }

    public Space getOwned(User user, String spaceId) {
        Space space = spaceRepository.findById(spaceId).orElseThrow(() -> new NotFoundException("Space not found."));
        if (!space.getUser().getId().equals(user.getId()) && user.getRole() != User.Role.ADMIN) {
            throw new ForbiddenException("You do not have access to this Space.");
        }
        return space;
    }

    private SpaceResponse toResponse(Space s, int projectCount) {
        return new SpaceResponse(s.getId(), s.getName(), s.getDescription(), s.getColorTheme(), projectCount, s.getCreatedAt());
    }
}
