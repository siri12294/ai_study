package com.aistudy.companion.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end coverage of: registration, login, and - critically - that one
 * user's JWT cannot read another user's Space (Project-level data isolation
 * is a named security requirement in the PRD).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthAndIsolationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper mapper;

    private String registerAndLogin(String email) throws Exception {
        String body = mapper.writeValueAsString(Map.of(
                "email", email, "displayName", "Test User", "password", "SuperSecret123"));
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).get("token").asText();
    }

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/spaces")).andExpect(status().isUnauthorized());
    }

    @Test
    void registeredUserCanCreateAndListTheirOwnSpace() throws Exception {
        String token = registerAndLogin("alice@example.com");

        String createBody = mapper.writeValueAsString(Map.of("name", "Machine Learning", "description", "ML study space"));
        mockMvc.perform(post("/api/spaces").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Machine Learning"));

        mockMvc.perform(get("/api/spaces").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Machine Learning"));
    }

    @Test
    void userCannotAccessAnotherUsersSpace() throws Exception {
        String aliceToken = registerAndLogin("alice2@example.com");
        String bobToken = registerAndLogin("bob2@example.com");

        String createBody = mapper.writeValueAsString(Map.of("name", "Alice's Private Space", "description", "secret"));
        String created = mockMvc.perform(post("/api/spaces").header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String spaceId = mapper.readTree(created).get("id").asText();

        // Bob should not be able to create a Project inside Alice's Space.
        String projectBody = mapper.writeValueAsString(Map.of("name", "Snooping", "description", "d", "goal", "g"));
        mockMvc.perform(post("/api/spaces/" + spaceId + "/projects").header("Authorization", "Bearer " + bobToken)
                        .contentType(MediaType.APPLICATION_JSON).content(projectBody))
                .andExpect(status().isForbidden());
    }

    @Test
    void nonAdminCannotReachAdminEndpoints() throws Exception {
        String token = registerAndLogin("regular@example.com");
        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
