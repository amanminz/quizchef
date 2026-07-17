package io.quizchef.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Exports the live OpenAPI specification to {@code build/openapi.json} so the
 * frontend can generate its TypeScript API types from it ({@code npm run
 * generate:api}) instead of hand-maintaining request/response models. The
 * export is a real assertion-backed test — if the spec ever stops being
 * served, client generation breaks here first, not in the frontend build.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class OpenApiSpecExportTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exportsTheOpenApiSpecificationForClientGeneration() throws Exception {
        String specification = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(specification).contains("\"openapi\"").contains("/api/v1/");

        Path output = Path.of(System.getProperty("openapi.output", "build/openapi.json"));
        Files.createDirectories(output.getParent());
        Files.writeString(output, specification, StandardCharsets.UTF_8);
    }
}
