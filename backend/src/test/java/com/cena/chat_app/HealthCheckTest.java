package com.cena.chat_app;

import com.cena.chat_app.config.TestMongoDBConfiguration;
import com.cena.chat_app.config.TestRedisConfiguration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestRedisConfiguration.class, TestMongoDBConfiguration.class})
class HealthCheckTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testHealthEndpoint() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:" + port + "/actuator/health"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertTrue(response.statusCode() == 200 || response.statusCode() == 503);

        JsonNode jsonResponse = objectMapper.readTree(response.body());
        assertNotNull(jsonResponse.get("status"));
        String status = jsonResponse.get("status").asText();
        assertTrue(status.equals("UP") || status.equals("DOWN") || status.equals("OUT_OF_SERVICE"));
    }

    @Test
    void testReadinessProbe() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:" + port + "/actuator/health/readiness"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());

        JsonNode jsonResponse = objectMapper.readTree(response.body());
        assertNotNull(jsonResponse.get("status"));
        assertEquals("UP", jsonResponse.get("status").asText());

        JsonNode components = jsonResponse.get("components");
        assertNotNull(components);
        assertNotNull(components.get("mongo"));
        assertNotNull(components.get("redis"));
        assertEquals("UP", components.get("mongo").get("status").asText());
        assertEquals("UP", components.get("redis").get("status").asText());
    }

    @Test
    void testLivenessProbe() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:" + port + "/actuator/health/liveness"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertTrue(response.statusCode() == 200 || response.statusCode() == 503);

        JsonNode jsonResponse = objectMapper.readTree(response.body());
        assertNotNull(jsonResponse.get("status"));
    }

    @Test
    void testHealthIndicatorsPresent() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:" + port + "/actuator/health"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode jsonResponse = objectMapper.readTree(response.body());

        if (jsonResponse.has("components")) {
            JsonNode components = jsonResponse.get("components");
            assertTrue(components.has("mongo") || components.has("redis") ||
                      components.has("minio") || components.has("smtp"));
        }
    }
}
