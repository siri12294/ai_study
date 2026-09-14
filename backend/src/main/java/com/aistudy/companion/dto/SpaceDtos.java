package com.aistudy.companion.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public class SpaceDtos {
    public record CreateSpaceRequest(@NotBlank String name, String description, String colorTheme) {}

    public record SpaceResponse(String id, String name, String description, String colorTheme,
                                 int projectCount, Instant createdAt) {}
}
