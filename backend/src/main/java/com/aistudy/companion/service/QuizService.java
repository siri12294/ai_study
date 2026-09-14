package com.aistudy.companion.service;

import com.aistudy.companion.ai.AiEngine;
import com.aistudy.companion.ai.AiModels;
import com.aistudy.companion.ai.RetrievalService;
import com.aistudy.companion.dto.QuizDtos.*;
import com.aistudy.companion.entity.*;
import com.aistudy.companion.exception.ConflictException;
import com.aistudy.companion.exception.NotFoundException;
import com.aistudy.companion.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Adaptive Quiz & Assessment.
 *
 * Question selection (selectNextConceptAndDifficulty) considers, per the
 * PRD's explicit instruction not to do naive "wrong->easy, right->hard":
 *   - current mastery per concept (prioritize weaker/lower-evidence concepts)
 *   - how recently a concept was tested (avoid immediate repeats)
 *   - difficulty is derived from the *target* concept's current mastery band,
 *     not purely from the previous answer's correctness
 *
 * Quiz completion triggers the learning workflow described in the PRD:
 * Quiz Completed -> Evaluate -> Update Mastery -> Detect Weakness ->
 * Generate Insight -> Recommend Next Action (see finishIfDone()).
 */
@Service
public class QuizService {

    private final QuizAttemptRepository attemptRepository;
    private final QuizQuestionRepository questionRepository;
    private final ConceptRepository conceptRepository;
    private final MasteryRepository masteryRepository;
    private final RetrievalService retrievalService;
    private final AiEngine aiEngine;
    private final MasteryService masteryService;
    private final ActivityService activityService;
    private final RecommendationService recommendationService;
    private final ObjectMapper mapper = new ObjectMapper();

    public QuizService(QuizAttemptRepository attemptRepository, QuizQuestionRepository questionRepository,
                        ConceptRepository conceptRepository, MasteryRepository masteryRepository,
                        RetrievalService retrievalService, AiEngine aiEngine, MasteryService masteryService,
                        ActivityService activityService, RecommendationService recommendationService) {
        this.attemptRepository = attemptRepository;
        this.questionRepository = questionRepository;
        this.conceptRepository = conceptRepository;
        this.masteryRepository = masteryRepository;
        this.retrievalService = retrievalService;
        this.aiEngine = aiEngine;
        this.masteryService = masteryService;
        this.activityService = activityService;
        this.recommendationService = recommendationService;
    }

    private static final int DEFAULT_QUESTION_COUNT = 5;

    @Transactional
    public StartQuizResponse start(Project project, User user, StartQuizRequest request) {
        List<Concept> concepts = conceptRepository.findByProjectId(project.getId());
        if (concepts.isEmpty()) {
            throw new ConflictException("This Project has no concepts yet. Upload and process material first.");
        }
        int total = request.questionCount() != null ? Math.max(1, Math.min(15, request.questionCount())) : DEFAULT_QUESTION_COUNT;

        QuizAttempt attempt = attemptRepository.save(QuizAttempt.builder().project(project).plannedQuestionCount(total).build());
        QuizQuestion first = generateNext(project, attempt, concepts, Set.of(), 0);

        activityService.record(user, project, "QUIZ_STARTED", "{\"attemptId\":\"" + attempt.getId() + "\"}", null);

        return new StartQuizResponse(attempt.getId(), toQuestionResponse(first), total);
    }

    @Transactional
    public AnswerResultResponse submitAnswer(Project project, User user, String attemptId, String questionId,
                                              SubmitAnswerRequest request) {
        QuizAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new NotFoundException("Quiz attempt not found."));
        if (!attempt.getProject().getId().equals(project.getId())) throw new NotFoundException("Quiz attempt not found.");

        List<QuizQuestion> answered = questionRepository.findByQuizAttemptIdOrderByOrderIndexAsc(attemptId);
        QuizQuestion question = answered.stream().filter(q -> q.getId().equals(questionId)).findFirst()
                .orElseThrow(() -> new NotFoundException("Question not found in this attempt."));
        if (question.getUserAnswer() != null) throw new ConflictException("This question has already been answered.");

        boolean isCorrect;
        double score;
        String feedback;
        String correctAnswerForDisplay;

        if (question.getType() == QuizQuestion.Type.MCQ) {
            isCorrect = normalize(question.getCorrectAnswer()).equals(normalize(request.answer()));
            score = isCorrect ? 1.0 : 0.0;
            feedback = isCorrect ? "Correct." : ("Not quite. The correct answer was: " + question.getCorrectAnswer());
            correctAnswerForDisplay = question.getCorrectAnswer();
        } else {
            AiModels.GradingResult grading = aiEngine.gradeOpenAnswer(project.getId(), user.getId(),
                    question.getPrompt(), question.getCorrectAnswer(), request.answer());
            isCorrect = grading.isCorrect();
            score = grading.score();
            feedback = grading.feedback();
            correctAnswerForDisplay = question.getCorrectAnswer(); // key points, shown after grading only
        }

        question.setUserAnswer(request.answer());
        question.setIsCorrect(isCorrect);
        question.setScore(score);
        question.setAiFeedback(feedback);
        questionRepository.save(question);

        if (question.getConcept() != null) {
            masteryService.applyEvidence(project, question.getConcept(), score, question.getDifficulty());
        }
        activityService.record(user, project, "QUESTION_ANSWERED",
                "{\"attemptId\":\"" + attemptId + "\",\"correct\":" + isCorrect + "}", null);

        int answeredCount = (int) answered.stream().filter(q -> q.getUserAnswer() != null).count() + 1;
        boolean isDone = answeredCount >= attempt.getPlannedQuestionCount();

        if (isDone) {
            double finalScore = finishAttempt(project, user, attempt);
            return new AnswerResultResponse(isCorrect, score, feedback, correctAnswerForDisplay, null, true, finalScore);
        }

        List<Concept> concepts = conceptRepository.findByProjectId(project.getId());
        Set<String> recentlyTested = new HashSet<>();
        for (QuizQuestion q : answered) if (q.getConcept() != null) recentlyTested.add(q.getConcept().getId());
        QuizQuestion next = generateNext(project, attempt, concepts, recentlyTested, answeredCount);

        return new AnswerResultResponse(isCorrect, score, feedback, correctAnswerForDisplay, toQuestionResponse(next), false, null);
    }

    private double finishAttempt(Project project, User user, QuizAttempt attempt) {
        List<QuizQuestion> all = questionRepository.findByQuizAttemptIdOrderByOrderIndexAsc(attempt.getId());
        double avg = all.stream().mapToDouble(q -> q.getScore() == null ? 0.0 : q.getScore()).average().orElse(0.0);
        attempt.setStatus(QuizAttempt.Status.COMPLETED);
        attempt.setCompletedAt(java.time.Instant.now());
        attempt.setFinalScore(avg);
        attemptRepository.save(attempt);

        // Learning workflow: Quiz Completed -> Evaluate -> Update Mastery (already done per-answer above)
        // -> Detect Weakness -> Generate Insight -> Recommend Next Action.
        activityService.record(user, project, "QUIZ_COMPLETED",
                "{\"attemptId\":\"" + attempt.getId() + "\",\"score\":" + avg + "}",
                "quiz-completed:" + attempt.getId());
        recommendationService.generateForProject(project, user.getId());

        return avg;
    }

    /**
     * Selects the next concept using mastery (lower = higher priority) with a
     * penalty for concepts tested very recently in this same attempt, then
     * derives difficulty from that concept's mastery band. This intentionally
     * does not simply increase difficulty after a correct answer or decrease
     * it after a wrong one - it targets whichever concept currently has the
     * weakest or least evidence-backed mastery.
     */
    private QuizQuestion generateNext(Project project, QuizAttempt attempt, List<Concept> concepts,
                                       Set<String> recentlyTestedConceptIds, int orderIndex) {
        Concept target = selectConcept(project, concepts, recentlyTestedConceptIds);
        Mastery mastery = masteryRepository.findByProjectIdAndConceptId(project.getId(), target.getId()).orElse(null);
        double score = mastery == null ? 0.3 : mastery.getScore();
        int difficulty = score < 0.4 ? 1 : score < 0.6 ? 2 : score < 0.75 ? 3 : score < 0.9 ? 4 : 5;
        String type = (orderIndex % 3 == 2) ? "OPEN" : "MCQ"; // mix in open-ended questions roughly 1-in-3

        List<AiModels.RetrievedChunk> evidence = retrievalService.retrieve(project.getId(), target.getName());
        AiModels.GeneratedQuestion generated = aiEngine.generateQuestion(project.getId(), attempt.getProject().getUser().getId(),
                target.getName(), target.getDescription(), evidence, type, difficulty);

        String optionsJson = null;
        try {
            if (generated.options() != null) optionsJson = mapper.writeValueAsString(generated.options());
        } catch (Exception ignored) {}

        QuizQuestion question = QuizQuestion.builder()
                .quizAttempt(attempt).concept(target)
                .type(QuizQuestion.Type.valueOf(generated.type().toUpperCase()))
                .prompt(generated.prompt())
                .optionsJson(optionsJson)
                .correctAnswer(generated.correctAnswer())
                .difficulty(difficulty)
                .orderIndex(orderIndex)
                .build();
        return questionRepository.save(question);
    }

    private Concept selectConcept(Project project, List<Concept> concepts, Set<String> recentlyTested) {
        Map<String, Mastery> masteryByConcept = new HashMap<>();
        for (Mastery m : masteryRepository.findByProjectId(project.getId())) masteryByConcept.put(m.getConcept().getId(), m);

        return concepts.stream()
                .min(Comparator.comparingDouble(c -> {
                    Mastery m = masteryByConcept.get(c.getId());
                    double baseScore = m == null ? 0.2 : m.getScore(); // untested concepts prioritized (low "mastery")
                    double evidencePenalty = m == null ? 0.0 : Math.min(0.15, m.getEvidenceCount() * 0.02);
                    double recencyPenalty = recentlyTested.contains(c.getId()) ? 0.5 : 0.0;
                    return baseScore + evidencePenalty + recencyPenalty;
                }))
                .orElse(concepts.get(0));
    }

    private String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }

    private QuestionResponse toQuestionResponse(QuizQuestion q) {
        List<String> options = null;
        try {
            if (q.getOptionsJson() != null) options = List.of(mapper.readValue(q.getOptionsJson(), String[].class));
        } catch (Exception ignored) {}
        return new QuestionResponse(q.getId(), q.getType().name(), q.getPrompt(), options,
                q.getConcept() != null ? q.getConcept().getName() : null, q.getDifficulty(), q.getOrderIndex());
    }

    public List<QuizQuestion> questionsForAttempt(String attemptId) {
        return questionRepository.findByQuizAttemptIdOrderByOrderIndexAsc(attemptId);
    }

    public QuizAttempt getOwnedAttempt(Project project, String attemptId) {
        QuizAttempt attempt = attemptRepository.findById(attemptId).orElseThrow(() -> new NotFoundException("Quiz attempt not found."));
        if (!attempt.getProject().getId().equals(project.getId())) throw new NotFoundException("Quiz attempt not found.");
        return attempt;
    }

    public List<QuizAttempt> listForProject(String projectId) {
        return attemptRepository.findByProjectIdOrderByStartedAtDesc(projectId);
    }
}
