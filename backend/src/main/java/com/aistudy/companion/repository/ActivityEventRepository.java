package com.aistudy.companion.repository;

import com.aistudy.companion.entity.ActivityEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ActivityEventRepository extends JpaRepository<ActivityEvent, String> {
    List<ActivityEvent> findByProjectIdOrderByCreatedAtDesc(String projectId);
    List<ActivityEvent> findByUserIdOrderByCreatedAtDesc(String userId);
    Optional<ActivityEvent> findByIdempotencyKey(String idempotencyKey);
    List<ActivityEvent> findTop50ByOrderByCreatedAtDesc();
}
