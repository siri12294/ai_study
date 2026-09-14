package com.aistudy.companion.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TutorMessage {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id")
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Lob
    @Column(length = 8000)
    private String content;

    @Lob
    @Column(length = 4000)
    private String citationsJson;

    private boolean insufficientEvidence;

    @Builder.Default
    private Instant createdAt = Instant.now();

    public enum Role { USER, ASSISTANT }
}
