package com.aistudy.companion.dto;

import java.time.Instant;
import java.util.List;

public class MasteryDtos {
    public record MasteryResponse(String conceptId, String conceptName, double score, String trend,
                                   int evidenceCount, Instant updatedAt) {}

    public record MasteryPoint(Instant recordedAt, double score) {}

    public record GrowthResponse(String conceptId, String conceptName, String trend, List<MasteryPoint> history) {}
}
