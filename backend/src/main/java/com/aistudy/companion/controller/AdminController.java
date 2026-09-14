package com.aistudy.companion.controller;

import com.aistudy.companion.dto.AdminDtos.*;
import com.aistudy.companion.dto.AnalyticsDtos.GlobalAnalyticsResponse;
import com.aistudy.companion.dto.ActivityDtos.ActivityResponse;
import com.aistudy.companion.service.ActivityService;
import com.aistudy.companion.service.AdminService;
import com.aistudy.companion.service.AnalyticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin Dashboard API. Every endpoint here is gated by SecurityConfig
 * (`/api/admin/**` requires ROLE_ADMIN) so a non-admin JWT cannot reach any
 * of this, regardless of what the frontend does.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final AnalyticsService analyticsService;
    private final ActivityService activityService;

    public AdminController(AdminService adminService, AnalyticsService analyticsService, ActivityService activityService) {
        this.adminService = adminService;
        this.analyticsService = analyticsService;
        this.activityService = activityService;
    }

    @GetMapping("/users")
    public ResponseEntity<List<UserSummary>> users() {
        return ResponseEntity.ok(adminService.listUsers());
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<UserDetail> userDetail(@PathVariable String userId) {
        return ResponseEntity.ok(adminService.userDetail(userId));
    }

    @GetMapping("/analytics")
    public ResponseEntity<GlobalAnalyticsResponse> analytics() {
        return ResponseEntity.ok(analyticsService.global());
    }

    @GetMapping("/activity")
    public ResponseEntity<List<ActivityResponse>> activity() {
        return ResponseEntity.ok(activityService.recentGlobal(100));
    }

    @GetMapping("/health")
    public ResponseEntity<SystemHealth> health() {
        return ResponseEntity.ok(adminService.systemHealth());
    }
}
