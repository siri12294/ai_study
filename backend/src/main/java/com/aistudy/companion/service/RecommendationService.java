package com.aistudy.companion.service;

import com.aistudy.companion.ai.AiEngine;
import com.aistudy.companion.dto.RecommendationDtos.RecommendationResponse;
import com.aistudy.companion.entity.Mastery;
import com.aistudy.companion.entity.Project;
import com.aistudy.companion.entity.Recommendation;
import com.aistudy.companion.repository.RecommendationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Converts growth/mastery evidence into a concrete "what should I do next"
 * recommendation. Triggered by the Quiz-completed learning workflow (see
 * QuizService) and available for the Project dashboard.
 */
@Service
public class RecommendationService {

    private final RecommendationRepository repository;
    private final MasteryService masteryService;
    private final AiEngine aiEngine;

    public RecommendationService(RecommendationRepository repository, MasteryService masteryService, AiEngine aiEngine) {
        this.repository = repository;
        this.masteryService = masteryService;
        this.aiEngine = aiEngine;
    }

    @Transactional
    public Recommendation generateForProject(Project project, String userId) {
        List<Mastery> weakest = masteryService.weakestConcepts(project.getId(), 3);

        String text;
        String relatedConceptId = null;
        if (weakest.isEmpty()) {
            text = "Take your first quiz to establish a mastery baseline for this Project's concepts.";
        } else {
            Mastery weakest0 = weakest.get(0);
            relatedConceptId = weakest0.getConcept().getId();
            String summary = weakest.stream()
                    .map(m -> String.format("%s: %.0f%% mastery, trend %s, %d evidence points",
                            m.getConcept().getName(), m.getScore() * 100, m.getTrend(), m.getEvidenceCount()))
                    .collect(Collectors.joining("; "));
            String masterySummary = "Learner goal: " + (project.getGoal() == null ? "(none)" : project.getGoal())
                    + ". Weakest concepts: " + summary
                    + ". Write one specific next action referencing the weakest concept by name.";
            // AiEngine itself decides mock vs real model; if the real call fails or mock mode is on,
            // it falls back to our own templated text below so a recommendation is always useful.
            String aiText = aiEngine.generateRecommendation(project.getId(), userId, masterySummary);
            text = (aiText == null || aiText.isBlank() || aiText.equals(masterySummary))
                    ? templatedRecommendationText(weakest0) : aiText;
        }

        Recommendation rec = Recommendation.builder().project(project).text(text).relatedConceptId(relatedConceptId).build();
        return repository.save(rec);
    }

    private String templatedRecommendationText(Mastery weakest) {
        String concept = weakest.getConcept().getName();
        return switch (weakest.getTrend()) {
            case IMPROVING -> String.format(
                    "Your understanding of %s is improving. Keep the momentum with one more short quiz focused on %s to lock it in.",
                    concept, concept);
            case NEEDS_ATTENTION -> String.format(
                    "Your understanding of %s needs attention (%.0f%% mastery). Review the related material and complete another short assessment on %s.",
                    concept, weakest.getScore() * 100, concept);
            default -> String.format(
                    "%s is holding steady around %.0f%% mastery. Try an application-style question on %s to push it further.",
                    concept, weakest.getScore() * 100, concept);
        };
    }

    public List<RecommendationResponse> activeForProject(String projectId) {
        return repository.findByProjectIdAndStatusOrderByCreatedAtDesc(projectId, Recommendation.Status.ACTIVE).stream()
                .map(r -> new RecommendationResponse(r.getId(), r.getText(), r.getRelatedConceptId(), r.getStatus().name(), r.getCreatedAt()))
                .toList();
    }

    @Transactional
    public void dismiss(String recommendationId) {
        repository.findById(recommendationId).ifPresent(r -> {
            r.setStatus(Recommendation.Status.DISMISSED);
            repository.save(r);
        });
    }
}
