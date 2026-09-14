package com.aistudy.companion.repository;

import com.aistudy.companion.entity.MasterySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MasterySnapshotRepository extends JpaRepository<MasterySnapshot, String> {
    List<MasterySnapshot> findByProjectIdAndConceptIdOrderByRecordedAtAsc(String projectId, String conceptId);
    List<MasterySnapshot> findByProjectIdOrderByRecordedAtAsc(String projectId);
}
