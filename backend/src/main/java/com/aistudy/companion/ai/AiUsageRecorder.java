package com.aistudy.companion.ai;

import com.aistudy.companion.entity.AiUsageLog;
import com.aistudy.companion.repository.AiUsageLogRepository;
import org.springframework.stereotype.Component;

/**
 * Central place every AI call reports through, so latency/cost/success can
 * be inspected later from the Admin Dashboard ("why was this slow / how
 * much did it cost / which model / did it fail").
 */
@Component
public class AiUsageRecorder {

    private final AiUsageLogRepository repository;

    public AiUsageRecorder(AiUsageLogRepository repository) {
        this.repository = repository;
    }

    public void record(String projectId, String userId, String feature, AiModels.UsageMeta meta) {
        // Extremely rough cost estimate for observability purposes only (Claude Sonnet-class pricing ballpark).
        double costPerMillionIn = 3.0;
        double costPerMillionOut = 15.0;
        Double cost = null;
        if (meta.tokensIn() != null && meta.tokensOut() != null) {
            cost = (meta.tokensIn() / 1_000_000.0) * costPerMillionIn
                    + (meta.tokensOut() / 1_000_000.0) * costPerMillionOut;
        }

        AiUsageLog log = AiUsageLog.builder()
                .projectId(projectId)
                .userId(userId)
                .feature(feature)
                .model(meta.model())
                .tokensIn(meta.tokensIn())
                .tokensOut(meta.tokensOut())
                .latencyMs(meta.latencyMs())
                .estimatedCostUsd(cost)
                .success(meta.success())
                .errorMessage(meta.errorMessage())
                .build();
        repository.save(log);
    }
}
