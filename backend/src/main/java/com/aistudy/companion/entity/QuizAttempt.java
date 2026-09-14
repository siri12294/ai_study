package com.aistudy.companion.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class QuizAttempt {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id")
    private Project project;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Status status = Status.IN_PROGRESS;

    @Builder.Default
    private Instant startedAt = Instant.now();

    private Instant completedAt;

    private Double finalScore;

    @Builder.Default
    private int plannedQuestionCount = 5;

    public enum Status { IN_PROGRESS, COMPLETED }
}
