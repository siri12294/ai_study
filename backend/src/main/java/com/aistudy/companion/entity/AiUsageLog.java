package com.aistudy.companion.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AiUsageLog {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String projectId;
    private String userId;

    @Column(nullable = false)
    private String feature;

    private String model;

    private Integer tokensIn;
    private Integer tokensOut;
    private Long latencyMs;
    private Double estimatedCostUsd;

    @Builder.Default
    private boolean success = true;

    @Column(length = 1000)
    private String errorMessage;

    @Builder.Default
    private Instant createdAt = Instant.now();
}
