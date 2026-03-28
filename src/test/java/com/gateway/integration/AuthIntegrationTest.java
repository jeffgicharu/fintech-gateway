package com.gateway.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    @DisplayName("Register a new API client and receive credentials")
    void register_returnsApiKeyAndSecret() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "clientId", "test-client-1",
                        "name", "Test App"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.apiKey").exists())
                .andExpect(jsonPath("$.secret").exists())
                .andExpect(jsonPath("$.clientId").value("test-client-1"));
    }

    @Test
    @DisplayName("Reject duplicate client registration")
    void register_duplicate_returns409() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "clientId", "dup-client", "name", "Duplicate"));

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Exchange API key + secret for JWT token")
    void token_validCredentials_returnsJwt() throws Exception {
        // Register
        MvcResult regResult = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "clientId", "token-test", "name", "Token Test"))))
                .andReturn();

        Map<String, String> creds = objectMapper.readValue(
                regResult.getResponse().getContentAsString(), Map.class);

        // Get token
        mockMvc.perform(post("/api/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "apiKey", creds.get("apiKey"),
                        "secret", creds.get("secret")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.clientId").value("token-test"));
    }

    @Test
    @DisplayName("Reject invalid API key")
    void token_invalidKey_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "apiKey", "fake-key", "secret", "fake-secret"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Protected endpoint rejects unauthenticated request")
    void gateway_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/gateway/stats"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Protected endpoint accepts valid JWT")
    void gateway_withToken_returns200() throws Exception {
        String token = registerAndGetToken("auth-test-client");

        mockMvc.perform(get("/api/gateway/stats")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private String registerAndGetToken(String clientId) throws Exception {
        MvcResult reg = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "clientId", clientId, "name", "Test"))))
                .andReturn();
        Map<String, String> creds = objectMapper.readValue(
                reg.getResponse().getContentAsString(), Map.class);

        MvcResult tok = mockMvc.perform(post("/api/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "apiKey", creds.get("apiKey"),
                        "secret", creds.get("secret")))))
                .andReturn();
        Map<String, String> tokenResp = objectMapper.readValue(
                tok.getResponse().getContentAsString(), Map.class);
        return tokenResp.get("token");
    }
}
