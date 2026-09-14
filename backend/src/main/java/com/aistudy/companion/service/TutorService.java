package com.aistudy.companion.service;

import com.aistudy.companion.ai.AiEngine;
import com.aistudy.companion.ai.AiModels;
import com.aistudy.companion.ai.RetrievalService;
import com.aistudy.companion.dto.TutorDtos.*;
import com.aistudy.companion.entity.Project;
import com.aistudy.companion.entity.TutorMessage;
import com.aistudy.companion.entity.User;
import com.aistudy.companion.repository.TutorMessageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The AI Tutor. Every request follows: understand request -> identify
 * Project context -> retrieve relevant evidence -> generate answer -> return
 * supporting sources (see AiEngine + RetrievalService). Conversation
 * continuity is provided by summarizing only the last few turns rather than
 * replaying the entire history, per "Persistent but Relevant Context".
 */
@Service
public class TutorService {

    private static final int HISTORY_TURNS = 6;

    private final TutorMessageRepository repository;
    private final RetrievalService retrievalService;
    private final AiEngine aiEngine;
    private final ActivityService activityService;
    private final ObjectMapper mapper = new ObjectMapper();

    public TutorService(TutorMessageRepository repository, RetrievalService retrievalService, AiEngine aiEngine,
                         ActivityService activityService) {
        this.repository = repository;
        this.retrievalService = retrievalService;
        this.aiEngine = aiEngine;
        this.activityService = activityService;
    }

    @Transactional
    public TutorMessageResponse ask(Project project, User user, String question) {
        repository.save(TutorMessage.builder().project(project).role(TutorMessage.Role.USER).content(question).build());

        String recentConversation = repository.findTop10ByProjectIdOrderByCreatedAtDesc(project.getId()).stream()
                .sorted(Comparator.comparing(TutorMessage::getCreatedAt))
                .limit(HISTORY_TURNS)
                .map(m -> m.getRole() + ": " + m.getContent())
                .collect(Collectors.joining("\n"));

        List<AiModels.RetrievedChunk> evidence = retrievalService.retrieve(project.getId(), question);
        AiModels.TutorAnswer answer = aiEngine.answerQuestion(project.getId(), user.getId(), project.getGoal(),
                recentConversation, evidence, question);

        String citationsJson;
        try {
            citationsJson = mapper.writeValueAsString(answer.citations());
        } catch (Exception e) {
            citationsJson = "[]";
        }

        TutorMessage assistantMsg = TutorMessage.builder()
                .project(project).role(TutorMessage.Role.ASSISTANT)
                .content(answer.answer())
                .citationsJson(citationsJson)
                .insufficientEvidence(answer.insufficientEvidence())
                .build();
        assistantMsg = repository.save(assistantMsg);

        activityService.record(user, project, "TUTOR_INTERACTION",
                "{\"insufficientEvidence\":" + answer.insufficientEvidence() + "}", null);

        return toResponse(assistantMsg);
    }

    public List<TutorMessageResponse> history(String projectId) {
        return repository.findByProjectIdOrderByCreatedAtAsc(projectId).stream().map(this::toResponse).toList();
    }

    private TutorMessageResponse toResponse(TutorMessage m) {
        List<Citation> citations = List.of();
        try {
            if (m.getCitationsJson() != null) {
                var refs = mapper.readValue(m.getCitationsJson(), AiModels.CitationRef[].class);
                citations = List.of(refs).stream()
                        .map(c -> new Citation(c.materialName(), c.page(), c.snippet()))
                        .toList();
            }
        } catch (Exception ignored) {}
        return new TutorMessageResponse(m.getId(), m.getRole().name(), m.getContent(), citations,
                m.isInsufficientEvidence(), m.getCreatedAt());
    }
}
