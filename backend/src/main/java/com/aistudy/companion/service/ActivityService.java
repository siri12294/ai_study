package com.aistudy.companion.service;

import com.aistudy.companion.dto.ActivityDtos.ActivityResponse;
import com.aistudy.companion.entity.ActivityEvent;
import com.aistudy.companion.entity.Project;
import com.aistudy.companion.entity.User;
import com.aistudy.companion.repository.ActivityEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Records learning events used for activity feeds, analytics, and as the
 * trigger for downstream workflows (see MasteryService / RecommendationService,
 * which react to "quiz completed" events). Idempotency keys prevent duplicate
 * state when an operation is retried (e.g. a flaky client resubmitting).
 */
@Service
public class ActivityService {

    private final ActivityEventRepository repository;

    public ActivityService(ActivityEventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ActivityEvent record(User user, Project project, String eventType, String payloadJson, String idempotencyKey) {
        if (idempotencyKey != null) {
            var existing = repository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) return existing.get();
        }
        ActivityEvent event = ActivityEvent.builder()
                .user(user)
                .project(project)
                .eventType(eventType)
                .payloadJson(payloadJson)
                .idempotencyKey(idempotencyKey)
                .build();
        return repository.save(event);
    }

    public List<ActivityResponse> recentForProject(String projectId, int limit) {
        return repository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .limit(limit)
                .map(e -> new ActivityResponse(e.getId(), e.getEventType(), e.getPayloadJson(), e.getCreatedAt(),
                        e.getProject() != null ? e.getProject().getName() : null,
                        e.getUser() != null ? e.getUser().getEmail() : null))
                .toList();
    }

    public List<ActivityResponse> recentForUser(String userId, int limit) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .limit(limit)
                .map(e -> new ActivityResponse(e.getId(), e.getEventType(), e.getPayloadJson(), e.getCreatedAt(),
                        e.getProject() != null ? e.getProject().getName() : null,
                        e.getUser() != null ? e.getUser().getEmail() : null))
                .toList();
    }

    public List<ActivityResponse> recentGlobal(int limit) {
        return repository.findTop50ByOrderByCreatedAtDesc().stream()
                .limit(limit)
                .map(e -> new ActivityResponse(e.getId(), e.getEventType(), e.getPayloadJson(), e.getCreatedAt(),
                        e.getProject() != null ? e.getProject().getName() : null,
                        e.getUser() != null ? e.getUser().getEmail() : null))
                .toList();
    }
}
