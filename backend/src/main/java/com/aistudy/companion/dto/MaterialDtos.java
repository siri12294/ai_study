package com.aistudy.companion.dto;

import java.time.Instant;

public class MaterialDtos {
    public record MaterialResponse(String id, String fileName, String status, Integer pageCount,
                                    String failureReason, Instant createdAt, Instant readyAt) {}
}
