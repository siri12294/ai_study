package com.aistudy.companion.service;

import com.aistudy.companion.dto.MasteryDtos.*;
import com.aistudy.companion.entity.*;
import com.aistudy.companion.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Owns the "how well am I learning it" side of the product: concept mastery
 * estimates and growth trend over time.
 *
 * Update rule (deliberately not "wrong -> easier, right -> harder"): each
 * piece of evidence (a quiz answer) nudges the mastery score toward the
 * observed performance, weighted by how hard the question was. Getting an
 * easy question right barely moves the needle; getting a hard question right
 * moves it more; getting an easy question wrong drops it more than missing a
 * hard one. This reflects that a wrong answer on "recall" material is
 * stronger evidence of a gap than struggling with "analysis" material.
 */
@Service
public class MasteryService {

    private static final double LEARNING_RATE = 0.35;

    private final ConceptRepository conceptRepository;
    private final MasteryRepository masteryRepository;
    private final MasterySnapshotRepository snapshotRepository;

    public MasteryService(ConceptRepository conceptRepository, MasteryRepository masteryRepository,
                           MasterySnapshotRepository snapshotRepository) {
        this.conceptRepository = conceptRepository;
        this.masteryRepository = masteryRepository;
        this.snapshotRepository = snapshotRepository;
    }

    @Transactional
    public Concept getOrCreateConcept(Project project, String name, String description) {
        return conceptRepository.findByProjectIdAndNameIgnoreCase(project.getId(), name)
                .orElseGet(() -> conceptRepository.save(Concept.builder()
                        .project(project).name(name).description(description).build()));
    }

    @Transactional
    public Mastery applyEvidence(Project project, Concept concept, double observedScore, int difficulty) {
        Mastery mastery = masteryRepository.findByProjectIdAndConceptId(project.getId(), concept.getId())
                .orElseGet(() -> Mastery.builder().project(project).concept(concept).score(0.3).build());

        double difficultyWeight = 0.6 + (difficulty / 5.0) * 0.8; // harder evidence moves the needle more
        double delta = (observedScore - mastery.getScore()) * LEARNING_RATE * difficultyWeight;
        double newScore = clamp(mastery.getScore() + delta, 0.0, 1.0);

        double previous = mastery.getScore();
        mastery.setScore(newScore);
        mastery.setEvidenceCount(mastery.getEvidenceCount() + 1);
        mastery.setTrend(newScore - previous > 0.03 ? Mastery.Trend.IMPROVING
                : newScore - previous < -0.03 ? Mastery.Trend.NEEDS_ATTENTION
                : mastery.getEvidenceCount() < 3 ? Mastery.Trend.STABLE
                : (newScore < 0.55 ? Mastery.Trend.NEEDS_ATTENTION : Mastery.Trend.STABLE));
        mastery = masteryRepository.save(mastery);

        snapshotRepository.save(MasterySnapshot.builder().project(project).concept(concept).score(newScore).build());
        return mastery;
    }

    private double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public List<MasteryResponse> listForProject(String projectId) {
        return masteryRepository.findByProjectId(projectId).stream()
                .map(m -> new MasteryResponse(m.getConcept().getId(), m.getConcept().getName(), m.getScore(),
                        m.getTrend().name(), m.getEvidenceCount(), m.getUpdatedAt()))
                .sorted((a, b) -> Double.compare(b.score(), a.score()))
                .toList();
    }

    public List<GrowthResponse> growthForProject(String projectId) {
        return masteryRepository.findByProjectId(projectId).stream()
                .map(m -> {
                    List<MasteryPoint> history = snapshotRepository
                            .findByProjectIdAndConceptIdOrderByRecordedAtAsc(projectId, m.getConcept().getId())
                            .stream().map(s -> new MasteryPoint(s.getRecordedAt(), s.getScore())).toList();
                    return new GrowthResponse(m.getConcept().getId(), m.getConcept().getName(), m.getTrend().name(), history);
                })
                .toList();
    }

    public List<Mastery> weakestConcepts(String projectId, int limit) {
        return masteryRepository.findByProjectId(projectId).stream()
                .sorted((a, b) -> Double.compare(a.getScore(), b.getScore()))
                .limit(limit)
                .toList();
    }

    public double overallProgress(String projectId) {
        var list = masteryRepository.findByProjectId(projectId);
        if (list.isEmpty()) return 0.0;
        return list.stream().mapToDouble(Mastery::getScore).average().orElse(0.0);
    }
}
