package com.aistudy.companion.service;

import com.aistudy.companion.entity.*;
import com.aistudy.companion.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Core business-logic test: mastery must move toward observed evidence,
 * weighted by difficulty - NOT a naive "right -> harder, wrong -> easier"
 * rule. This directly covers a PRD requirement called out as an explicit
 * anti-pattern to avoid.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MasteryServiceTest {

    @Autowired private MasteryService masteryService;
    @Autowired private UserRepository userRepository;
    @Autowired private SpaceRepository spaceRepository;
    @Autowired private ProjectRepository projectRepository;

    private Project project;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.builder().email("t@example.com").displayName("T")
                .passwordHash("x").role(User.Role.USER).build());
        Space space = spaceRepository.save(Space.builder().user(user).name("Space").build());
        project = projectRepository.save(Project.builder().space(space).user(user).name("Project").goal("Learn").build());
    }

    @Test
    void masteryIncreasesMoreForCorrectHardQuestionThanCorrectEasyQuestion() {
        Concept conceptA = masteryService.getOrCreateConcept(project, "Concept A", null);
        Concept conceptB = masteryService.getOrCreateConcept(project, "Concept B", null);

        Mastery afterEasyCorrect = masteryService.applyEvidence(project, conceptA, 1.0, 1);
        Mastery afterHardCorrect = masteryService.applyEvidence(project, conceptB, 1.0, 5);

        double easyDelta = afterEasyCorrect.getScore() - 0.3;
        double hardDelta = afterHardCorrect.getScore() - 0.3;

        assertTrue(hardDelta > easyDelta, "A correct hard-question answer should move mastery up more than a correct easy one");
    }

    @Test
    void wrongAnswerOnEasyQuestionDropsMasteryMoreThanStrugglingOnHardQuestion() {
        Concept conceptA = masteryService.getOrCreateConcept(project, "Concept A", null);
        Concept conceptB = masteryService.getOrCreateConcept(project, "Concept B", null);
        masteryService.applyEvidence(project, conceptA, 0.6, 3);
        masteryService.applyEvidence(project, conceptB, 0.6, 3);

        Mastery afterWrongEasy = masteryService.applyEvidence(project, conceptA, 0.0, 1);
        Mastery afterPartialHard = masteryService.applyEvidence(project, conceptB, 0.4, 5);

        assertTrue(afterWrongEasy.getScore() < afterPartialHard.getScore());
    }

    @Test
    void evidenceCountIncrementsAndScoreStaysWithinBounds() {
        Concept concept = masteryService.getOrCreateConcept(project, "Concept X", null);
        for (int i = 0; i < 10; i++) {
            masteryService.applyEvidence(project, concept, 1.0, 5);
        }
        var mastery = masteryService.listForProject(project.getId()).get(0);
        assertEquals(10, mastery.evidenceCount());
        assertTrue(mastery.score() <= 1.0 && mastery.score() >= 0.0);
    }
}
