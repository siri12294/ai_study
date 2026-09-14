package com.aistudy.companion.repository;

import com.aistudy.companion.entity.Mastery;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface MasteryRepository extends JpaRepository<Mastery, String> {
    List<Mastery> findByProjectId(String projectId);
    Optional<Mastery> findByProjectIdAndConceptId(String projectId, String conceptId);
}
