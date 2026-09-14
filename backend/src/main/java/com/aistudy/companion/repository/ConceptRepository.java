package com.aistudy.companion.repository;

import com.aistudy.companion.entity.Concept;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ConceptRepository extends JpaRepository<Concept, String> {
    List<Concept> findByProjectId(String projectId);
    Optional<Concept> findByProjectIdAndNameIgnoreCase(String projectId, String name);
}
