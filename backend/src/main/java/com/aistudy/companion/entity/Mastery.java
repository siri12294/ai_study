package com.aistudy.companion.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Mastery {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id")
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "concept_id")
    private Concept concept;

    @Builder.Default
    private double score = 0.0;

    @Builder.Default
    private int evidenceCount = 0;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Trend trend = Trend.STABLE;

    @Builder.Default
    private Instant updatedAt = Instant.now();

    public enum Trend { IMPROVING, STABLE, NEEDS_ATTENTION }
}
