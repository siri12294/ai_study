package com.aistudy.companion.repository;

import com.aistudy.companion.entity.Material;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MaterialRepository extends JpaRepository<Material, String> {
    List<Material> findByProjectIdOrderByCreatedAtDesc(String projectId);
}
