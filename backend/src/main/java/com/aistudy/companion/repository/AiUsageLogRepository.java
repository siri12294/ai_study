package com.aistudy.companion.repository;

import com.aistudy.companion.entity.AiUsageLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AiUsageLogRepository extends JpaRepository<AiUsageLog, String> {
    List<AiUsageLog> findByProjectIdOrderByCreatedAtDesc(String projectId);
    List<AiUsageLog> findTop200ByOrderByCreatedAtDesc();
}
