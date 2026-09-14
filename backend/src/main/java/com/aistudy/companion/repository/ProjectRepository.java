package com.aistudy.companion.repository;

import com.aistudy.companion.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, String> {
    List<Project> findBySpaceIdOrderByUpdatedAtDesc(String spaceId);
    List<Project> findByUserIdOrderByUpdatedAtDesc(String userId);
}
