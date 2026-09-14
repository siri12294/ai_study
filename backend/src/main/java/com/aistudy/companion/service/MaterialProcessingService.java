package com.aistudy.companion.service;

import com.aistudy.companion.entity.Material;
import com.aistudy.companion.entity.MaterialChunk;
import com.aistudy.companion.repository.MaterialChunkRepository;
import com.aistudy.companion.repository.MaterialRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs the actual document-processing pipeline on a background thread:
 *   Queued -> Processing/OCR* -> Extraction -> Knowledge Extraction -> Ready|Failed
 *
 * This is deliberately a SEPARATE bean from MaterialService. Spring's
 * @Async only takes effect on calls that go through the Spring proxy, which
 * means a method can't reliably kick off its own @Async work by calling
 * another method in the *same* class (self-invocation bypasses the proxy
 * and runs synchronously instead - a well-known Spring AOP gotcha that would
 * silently defeat "Asynchronous by Design" here). Keeping this pipeline in
 * its own bean means MaterialService.upload() calling
 * materialProcessingService.processAsync(...) is a normal cross-bean call
 * and is genuinely non-blocking.
 *
 * Retries for a single job are handled with a plain loop rather than
 * recursive self-calls, for the same reason - and because a retry doesn't
 * need to hop threads again, just try again within the job it's already on.
 *
 * *Note: this prototype extracts embedded text via PDFBox. True OCR for
 * scanned/image-only pages is out of scope here - see README limitations.
 */
@Service
public class MaterialProcessingService {

    private static final Logger log = LoggerFactory.getLogger(MaterialProcessingService.class);
    private static final int WORDS_PER_CHUNK = 350;
    private static final int MAX_ATTEMPTS = 3;

    private final MaterialRepository materialRepository;
    private final MaterialChunkRepository chunkRepository;
    private final MasteryService masteryService;

    public MaterialProcessingService(MaterialRepository materialRepository, MaterialChunkRepository chunkRepository,
                                      MasteryService masteryService) {
        this.materialRepository = materialRepository;
        this.chunkRepository = chunkRepository;
        this.masteryService = masteryService;
    }

    @Async("backgroundExecutor")
    public void processAsync(String materialId) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            boolean succeeded = attemptOnce(materialId, attempt);
            if (succeeded) return;
        }
    }

    /**
     * One processing attempt. Note: because this is called from processAsync()
     * within the same bean, a @Transactional annotation here would NOT take
     * effect (same self-invocation issue described above) - so we don't add
     * one. Each individual repository call below is already transactional on
     * its own via Spring Data, and processing is written to be safely
     * re-run (deleteByMaterialId before re-chunking) so partial progress from
     * an interrupted attempt is cleaned up on retry rather than duplicated.
     */
    protected boolean attemptOnce(String materialId, int attemptNumber) {
        Material material = materialRepository.findById(materialId).orElse(null);
        if (material == null) return true; // nothing to do, don't retry a deleted material
        if (material.getStatus() == Material.Status.READY) return true; // idempotent: already done

        material.setStatus(Material.Status.PROCESSING);
        material.setProcessingAttempts(attemptNumber);
        materialRepository.save(material);

        try {
            chunkRepository.deleteByMaterialId(materialId); // clean any partial attempt before retry

            ExtractionResult result = extractText(material.getStoredPath());
            List<MaterialChunk> chunks = chunkText(material, result);
            chunkRepository.saveAll(chunks);

            List<String> concepts = extractCandidateConcepts(result.fullText());
            for (String c : concepts) {
                masteryService.getOrCreateConcept(material.getProject(), c, "Auto-detected from " + material.getFileName());
            }

            material.setPageCount(result.pageCount());
            material.setStatus(Material.Status.READY);
            material.setReadyAt(Instant.now());
            material.setFailureReason(null);
            materialRepository.save(material);
            log.info("Material {} processed on attempt {}: {} chunks, {} concepts",
                    materialId, attemptNumber, chunks.size(), concepts.size());
            return true;
        } catch (Exception e) {
            log.warn("Material processing attempt {} failed for {}: {}", attemptNumber, materialId, e.getMessage());
            if (attemptNumber >= MAX_ATTEMPTS) {
                material.setStatus(Material.Status.FAILED);
                material.setFailureReason(e.getMessage() == null ? "Unknown processing error" : e.getMessage());
                materialRepository.save(material);
                return true; // stop retrying, terminal state reached
            }
            material.setStatus(Material.Status.QUEUED);
            materialRepository.save(material);
            return false; // let the loop try again
        }
    }

    private record ExtractionResult(String fullText, Map<Integer, String> textByPage, int pageCount) {}

    private ExtractionResult extractText(String storedPath) throws IOException {
        File file = new File(storedPath);
        try (PDDocument doc = PDDocument.load(file)) {
            int pages = doc.getNumberOfPages();
            Map<Integer, String> byPage = new LinkedHashMap<>();
            StringBuilder all = new StringBuilder();
            PDFTextStripper stripper = new PDFTextStripper();
            for (int i = 1; i <= pages; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String pageText = stripper.getText(doc);
                byPage.put(i, pageText);
                all.append(pageText).append("\n");
            }
            if (all.toString().isBlank()) {
                throw new IOException("No extractable text found (the PDF may be scanned/image-only; OCR is not enabled in this prototype).");
            }
            return new ExtractionResult(all.toString(), byPage, pages);
        }
    }

    private List<MaterialChunk> chunkText(Material material, ExtractionResult result) {
        List<MaterialChunk> chunks = new ArrayList<>();
        int index = 0;
        for (var entry : result.textByPage().entrySet()) {
            int page = entry.getKey();
            String[] words = entry.getValue().trim().split("\\s+");
            if (words.length == 0 || words[0].isBlank()) continue;
            for (int i = 0; i < words.length; i += WORDS_PER_CHUNK) {
                int end = Math.min(i + WORDS_PER_CHUNK, words.length);
                String content = String.join(" ", Arrays.asList(words).subList(i, end));
                if (content.isBlank()) continue;
                chunks.add(MaterialChunk.builder()
                        .material(material).project(material.getProject())
                        .chunkIndex(index++).pageNumber(page).content(content)
                        .build());
            }
        }
        return chunks;
    }

    /**
     * Very lightweight "knowledge extraction": looks for capitalized
     * multi-word phrases that recur across the document, on the theory that
     * repeated proper-noun-like phrases in study material are likely to be
     * named concepts/terms. This is a heuristic stand-in for a real
     * NLP/LLM-based concept extractor (see README).
     */
    private List<String> extractCandidateConcepts(String text) {
        Pattern phrase = Pattern.compile("\\b([A-Z][a-zA-Z]{2,}(?:\\s+[A-Z][a-zA-Z]{2,}){0,2})\\b");
        Matcher m = phrase.matcher(text);
        Map<String, Integer> counts = new LinkedHashMap<>();
        while (m.find()) {
            String candidate = m.group(1).trim();
            if (candidate.split("\\s+").length > 3) continue;
            counts.merge(candidate, 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .filter(e -> e.getValue() >= 2)
                .sorted((a, b) -> b.getValue() - a.getValue())
                .map(Map.Entry::getKey)
                .distinct()
                .limit(8)
                .toList();
    }
}
