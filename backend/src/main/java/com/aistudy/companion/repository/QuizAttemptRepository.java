package com.aistudy.companion.repository;

import com.aistudy.companion.entity.QuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, String> {
    List<QuizAttempt> findByProjectIdOrderByStartedAtDesc(String projectId);
}
