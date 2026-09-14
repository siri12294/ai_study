package com.aistudy.companion.service;

import com.aistudy.companion.ai.AiModels;
import com.aistudy.companion.ai.RetrievalService;
import com.aistudy.companion.entity.*;
import com.aistudy.companion.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the "Evidence Over Guessing" principle: a question that matches
 * material should retrieve it; a question with no lexical overlap with any
 * material should retrieve nothing, which is what drives the Tutor's
 * insufficient-evidence path (see AiEngineTest / TutorService).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RetrievalServiceTest {

    @Autowired private RetrievalService retrievalService;
    @Autowired private UserRepository userRepository;
    @Autowired private SpaceRepository spaceRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private MaterialRepository materialRepository;
    @Autowired private MaterialChunkRepository chunkRepository;

    private Project project;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.builder().email("r@example.com").displayName("R")
                .passwordHash("x").role(User.Role.USER).build());
        Space space = spaceRepository.save(Space.builder().user(user).name("Space").build());
        project = projectRepository.save(Project.builder().space(space).user(user).name("Project").build());

        Material material = materialRepository.save(Material.builder().project(project).fileName("notes.pdf")
                .storedPath("/tmp/notes.pdf").status(Material.Status.READY).build());

        chunkRepository.save(MaterialChunk.builder().material(material).project(project).chunkIndex(0)
                .pageNumber(1).content("Photosynthesis is the process plants use to convert sunlight into energy.").build());
        chunkRepository.save(MaterialChunk.builder().material(material).project(project).chunkIndex(1)
                .pageNumber(2).content("Mitochondria are the powerhouse of the cell and generate ATP.").build());
    }

    @Test
    void retrievesRelevantChunkForMatchingQuery() {
        List<AiModels.RetrievedChunk> results = retrievalService.retrieve(project.getId(), "How does photosynthesis work?");
        assertFalse(results.isEmpty());
        assertTrue(results.get(0).content().toLowerCase().contains("photosynthesis"));
    }

    @Test
    void returnsNoEvidenceForUnrelatedQuery() {
        List<AiModels.RetrievedChunk> results = retrievalService.retrieve(project.getId(), "quantum entanglement in string theory");
        assertTrue(results.isEmpty(), "Unrelated query should not retrieve unrelated material as false evidence");
        assertFalse(retrievalService.hasSufficientEvidence(results));
    }
}
