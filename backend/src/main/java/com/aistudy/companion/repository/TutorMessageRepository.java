package com.aistudy.companion.repository;

import com.aistudy.companion.entity.TutorMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TutorMessageRepository extends JpaRepository<TutorMessage, String> {
    List<TutorMessage> findByProjectIdOrderByCreatedAtAsc(String projectId);
    List<TutorMessage> findTop10ByProjectIdOrderByCreatedAtDesc(String projectId);
}
