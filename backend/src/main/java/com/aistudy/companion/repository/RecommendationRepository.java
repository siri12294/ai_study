package com.aistudy.companion.repository;

import com.aistudy.companion.entity.Recommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RecommendationRepository extends JpaRepository<Recommendation, String> {
    List<Recommendation> findByProjectIdAndStatusOrderByCreatedAtDesc(String projectId, Recommendation.Status status);
    List<Recommendation> findByProjectIdOrderByCreatedAtDesc(String projectId);
}
