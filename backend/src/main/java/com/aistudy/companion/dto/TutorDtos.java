package com.aistudy.companion.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;

public class TutorDtos {
    public record AskRequest(@NotBlank String message) {}

    public record Citation(String materialName, Integer page, String snippet) {}

    public record TutorMessageResponse(String id, String role, String content, List<Citation> citations,
                                        boolean insufficientEvidence, Instant createdAt) {}
}
