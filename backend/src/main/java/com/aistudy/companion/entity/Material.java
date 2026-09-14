package com.aistudy.companion.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Material {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id")
    private Project project;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private String storedPath;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Status status = Status.QUEUED;

    private Integer pageCount;

    @Column(length = 2000)
    private String failureReason;

    @Builder.Default
    private int processingAttempts = 0;

    @Builder.Default
    private Instant createdAt = Instant.now();

    private Instant readyAt;

    public enum Status { QUEUED, PROCESSING, READY, FAILED }
}
