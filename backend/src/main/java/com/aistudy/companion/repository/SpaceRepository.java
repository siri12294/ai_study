package com.aistudy.companion.repository;

import com.aistudy.companion.entity.Space;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SpaceRepository extends JpaRepository<Space, String> {
    List<Space> findByUserIdOrderByCreatedAtDesc(String userId);
}
