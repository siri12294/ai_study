package com.aistudy.companion.service;

import com.aistudy.companion.dto.AnalyticsDtos.*;
import com.aistudy.companion.entity.*;
import com.aistudy.companion.repository.*;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    private final TutorMessageRepository tutorMessageRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final ActivityEventRepository activityEventRepository;
    private final MasteryService masteryService;
    private final UserRepository userRepository;
    private final SpaceRepository spaceRepository;
    private final ProjectRepository projectRepository;
    private final MaterialRepository materialRepository;
    private final AiUsageLogRepository aiUsageLogRepository;

    public AnalyticsService(TutorMessageRepository tutorMessageRepository, QuizAttemptRepository quizAttemptRepository,
                             QuizQuestionRepository quizQuestionRepository, ActivityEventRepository activityEventRepository,
                             MasteryService masteryService, UserRepository userRepository, SpaceRepository spaceRepository,
                             ProjectRepository projectRepository, MaterialRepository materialRepository,
                             AiUsageLogRepository aiUsageLogRepository) {
        this.tutorMessageRepository = tutorMessageRepository;
        this.quizAttemptRepository = quizAttemptRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.activityEventRepository = activityEventRepository;
        this.masteryService = masteryService;
        this.userRepository = userRepository;
        this.spaceRepository = spaceRepository;
        this.projectRepository = projectRepository;
        this.materialRepository = materialRepository;
        this.aiUsageLogRepository = aiUsageLogRepository;
    }

    public ProjectAnalyticsResponse forProject(String projectId) {
        long tutorMessages = tutorMessageRepository.findByProjectIdOrderByCreatedAtAsc(projectId).size();
        List<QuizAttempt> attempts = quizAttemptRepository.findByProjectIdOrderByStartedAtDesc(projectId);
        long completed = attempts.stream().filter(a -> a.getStatus() == QuizAttempt.Status.COMPLETED).count();
        double avgScore = attempts.stream().filter(a -> a.getFinalScore() != null)
                .mapToDouble(QuizAttempt::getFinalScore).average().orElse(0.0);
        long questionsAnswered = attempts.stream()
                .flatMap(a -> quizQuestionRepository.findByQuizAttemptIdOrderByOrderIndexAsc(a.getId()).stream())
                .filter(q -> q.getUserAnswer() != null).count();

        Map<String, Long> activityByType = activityEventRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .collect(Collectors.groupingBy(ActivityEvent::getEventType, Collectors.counting()));

        return new ProjectAnalyticsResponse(tutorMessages, completed, questionsAnswered, avgScore,
                masteryService.listForProject(projectId), activityByType);
    }

    public GlobalAnalyticsResponse global() {
        long totalUsers = userRepository.count();
        long totalSpaces = spaceRepository.count();
        long totalProjects = projectRepository.count();
        long totalMaterials = materialRepository.count();
        long totalTutorMessages = tutorMessageRepository.count();
        long totalQuizzesCompleted = quizAttemptRepository.findAll().stream()
                .filter(a -> a.getStatus() == QuizAttempt.Status.COMPLETED).count();

        Map<String, Long> activityByType = activityEventRepository.findAll().stream()
                .collect(Collectors.groupingBy(ActivityEvent::getEventType, Collectors.counting()));

        Map<String, List<AiUsageLog>> byFeature = aiUsageLogRepository.findAll().stream()
                .collect(Collectors.groupingBy(AiUsageLog::getFeature));
        List<AiUsageSummary> usage = byFeature.entrySet().stream().map(e -> {
            var logs = e.getValue();
            double avgLatency = logs.stream().mapToLong(l -> l.getLatencyMs() == null ? 0 : l.getLatencyMs()).average().orElse(0);
            double totalCost = logs.stream().mapToDouble(l -> l.getEstimatedCostUsd() == null ? 0 : l.getEstimatedCostUsd()).sum();
            double successRate = logs.stream().filter(AiUsageLog::isSuccess).count() / (double) logs.size();
            return new AiUsageSummary(e.getKey(), logs.size(), avgLatency, totalCost, successRate);
        }).toList();

        return new GlobalAnalyticsResponse(totalUsers, totalSpaces, totalProjects, totalMaterials, totalTutorMessages,
                totalQuizzesCompleted, activityByType, usage);
    }
}
