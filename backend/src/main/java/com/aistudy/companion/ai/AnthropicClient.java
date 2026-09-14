package com.aistudy.companion.ai;

import com.fasterxml.jackson.databind.JsonNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * Thin wrapper around the Anthropic Messages API. Kept deliberately narrow
 * (one method) so the rest of the codebase depends on a small interface and
 * a different provider (OpenAI, Bedrock, etc.) could be substituted here
 * without touching Tutor/Quiz/Recommendation logic - see AiEngine.
 */
@Component
public class AnthropicClient {

    private static final Logger log = LoggerFactory.getLogger(AnthropicClient.class);

    private final WebClient webClient;
    private final String apiKey;
    private final String model;
    private final String baseUrl;

    public AnthropicClient(WebClient.Builder builder,
                            @Value("${app.ai.anthropic.api-key}") String apiKey,
                            @Value("${app.ai.anthropic.model}") String model,
                            @Value("${app.ai.anthropic.base-url}") String baseUrl) {
        this.webClient = builder.build();
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Sends a single-turn request with a system prompt and a user prompt and
     * returns the concatenated text of the response. Callers that need
     * structured data ask the model to return JSON-only in the prompt and
     * parse it themselves (see AiEngine).
     */
    public String complete(String systemPrompt, String userPrompt, int maxTokens) {
        if (!isConfigured()) {
            throw new IllegalStateException("Anthropic API key not configured");
        }
        Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", maxTokens,
                "system", systemPrompt,
                "messages", List.of(Map.of("role", "user", "content", userPrompt))
        );

        JsonNode response = webClient.post()
                .uri(baseUrl)
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        if (response == null || !response.has("content")) {
            log.warn("Unexpected Anthropic response shape: {}", response);
            throw new IllegalStateException("Empty response from AI provider");
        }

        StringBuilder sb = new StringBuilder();
        for (JsonNode block : response.get("content")) {
            if (block.has("text")) sb.append(block.get("text").asText());
        }
        return sb.toString();
    }
}
