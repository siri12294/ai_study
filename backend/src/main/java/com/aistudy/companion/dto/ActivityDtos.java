package com.aistudy.companion.dto;

import java.time.Instant;

public class ActivityDtos {
    public record ActivityResponse(String id, String eventType, String payloadJson, Instant createdAt,
                                    String projectName, String userEmail) {}
}
