package com.aistudy.companion.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class QuizQuestion {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_attempt_id")
    private QuizAttempt quizAttempt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "concept_id")
    private Concept concept;

    @Enumerated(EnumType.STRING)
    private Type type;

    @Lob
    @Column(length = 4000)
    private String prompt;

    @Lob
    @Column(length = 2000)
    private String optionsJson;

    @Lob
    @Column(length = 1000)
    private String correctAnswer;

    @Lob
    @Column(length = 4000)
    private String userAnswer;

    @Lob
    @Column(length = 2000)
    private String aiFeedback;

    private Boolean isCorrect;

    private Double score;

    @Column(nullable = false)
    private int difficulty;

    private int orderIndex;

    public enum Type { MCQ, OPEN }
}
