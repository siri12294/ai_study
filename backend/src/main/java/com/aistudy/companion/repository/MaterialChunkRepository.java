package com.aistudy.companion.repository;

import com.aistudy.companion.entity.MaterialChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MaterialChunkRepository extends JpaRepository<MaterialChunk, String> {
    List<MaterialChunk> findByProjectId(String projectId);
    List<MaterialChunk> findByMaterialId(String materialId);
    void deleteByMaterialId(String materialId);
}
