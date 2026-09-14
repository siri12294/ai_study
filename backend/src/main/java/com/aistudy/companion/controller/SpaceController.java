package com.aistudy.companion.controller;

import com.aistudy.companion.dto.SpaceDtos.*;

import com.aistudy.companion.security.CurrentUser;
import com.aistudy.companion.service.SpaceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/spaces")
public class SpaceController {

    private final SpaceService spaceService;
    private final CurrentUser currentUser;

    public SpaceController(SpaceService spaceService, CurrentUser currentUser) {
        this.spaceService = spaceService;
        this.currentUser = currentUser;
    }

    @PostMapping
    public ResponseEntity<SpaceResponse> create(@Valid @RequestBody CreateSpaceRequest request) {
        return ResponseEntity.ok(spaceService.create(currentUser.get(), request));
    }

    @GetMapping
    public ResponseEntity<List<SpaceResponse>> list() {
        return ResponseEntity.ok(spaceService.listForUser(currentUser.get()));
    }
}
