package com.aistudy.companion.ai;

import com.aistudy.companion.entity.MaterialChunk;
import com.aistudy.companion.repository.MaterialChunkRepository;
import com.aistudy.companion.repository.MaterialRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Retrieval layer for grounding the Tutor and Quiz generator in a Project's
 * materials.
 *
 * Implementation note: this uses lexical (TF-style term overlap) scoring
 * rather than vector embeddings, to keep the prototype dependency-free (no
 * external vector DB / embedding API required to run it). The interface
 * below (`retrieve`) is the seam where a real embedding-based retriever
 * would plug in - see README "Known Limitations / Future Improvements".
 */
@Service
public class RetrievalService {

    private static final Pattern WORD = Pattern.compile("[a-zA-Z0-9]+");
    private static final Set<String> STOPWORDS = Set.of(
            "the", "a", "an", "is", "are", "was", "were", "of", "to", "and", "or", "in", "on", "for",
            "what", "how", "why", "does", "do", "did", "this", "that", "it", "with", "as", "be", "can",
            "you", "i", "explain", "describe", "tell", "me", "about", "please"
    );

    private final MaterialChunkRepository chunkRepository;
    private final MaterialRepository materialRepository;

    @Value("${app.ai.retrieval.top-k}")
    private int topK;

    @Value("${app.ai.retrieval.min-relevance-score}")
    private double minScore;

    public RetrievalService(MaterialChunkRepository chunkRepository, MaterialRepository materialRepository) {
        this.chunkRepository = chunkRepository;
        this.materialRepository = materialRepository;
    }

    private List<String> terms(String text) {
        List<String> out = new ArrayList<>();
        var matcher = WORD.matcher(text.toLowerCase());
        while (matcher.find()) {
            String t = matcher.group();
            if (!STOPWORDS.contains(t) && t.length() > 2) out.add(t);
        }
        return out;
    }

    public List<AiModels.RetrievedChunk> retrieve(String projectId, String query) {
        List<MaterialChunk> chunks = chunkRepository.findByProjectId(projectId);
        if (chunks.isEmpty()) return List.of();

        List<String> queryTerms = terms(query);
        if (queryTerms.isEmpty()) return List.of();
        Set<String> queryTermSet = new HashSet<>(queryTerms);

        Map<String, String> materialNames = new HashMap<>();

        List<AiModels.RetrievedChunk> scored = new ArrayList<>();
        for (MaterialChunk chunk : chunks) {
            List<String> chunkTerms = terms(chunk.getContent());
            if (chunkTerms.isEmpty()) continue;
            long overlap = chunkTerms.stream().filter(queryTermSet::contains).distinct().count();
            if (overlap == 0) continue;

            // simple normalized overlap score: matched-unique-terms / query-unique-terms,
            // with a small boost for term density in the chunk.
            double score = (double) overlap / queryTermSet.size();
            double density = (double) chunkTerms.stream().filter(queryTermSet::contains).count() / chunkTerms.size();
            score = score * 0.75 + density * 0.25;

            String materialName = materialNames.computeIfAbsent(chunk.getMaterial().getId(),
                    id -> materialRepository.findById(id).map(m -> m.getFileName()).orElse("Unknown Material"));

            scored.add(new AiModels.RetrievedChunk(chunk.getId(), chunk.getMaterial().getId(), materialName,
                    chunk.getPageNumber(), chunk.getContent(), score));
        }

        return scored.stream()
                .sorted(Comparator.comparingDouble(AiModels.RetrievedChunk::relevanceScore).reversed())
                .filter(c -> c.relevanceScore() >= minScore)
                .limit(topK)
                .collect(Collectors.toList());
    }

    public boolean hasSufficientEvidence(List<AiModels.RetrievedChunk> chunks) {
        return !chunks.isEmpty();
    }
}
