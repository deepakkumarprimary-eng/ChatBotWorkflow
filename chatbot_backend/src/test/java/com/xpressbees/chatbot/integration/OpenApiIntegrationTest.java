package com.xpressbees.chatbot.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xpressbees.chatbot.config.OpenApiConfig;
import com.xpressbees.chatbot.controller.ApiConfigController;
import com.xpressbees.chatbot.controller.WorkflowController;
import com.xpressbees.chatbot.service.ApiConfigCacheService;
import com.xpressbees.chatbot.service.ApiConfigService;
import com.xpressbees.chatbot.service.WorkflowCacheService;
import com.xpressbees.chatbot.service.WorkflowService;
import org.junit.jupiter.api.Test;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.configuration.SpringDocUIConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springdoc.webmvc.ui.SwaggerConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for OpenAPI documentation endpoints.
 * Verifies that SpringDoc generates a valid OpenAPI spec with expected paths
 * and that the Swagger UI redirect endpoint is active when enabled.
 *
 * Uses @WebMvcTest with both controllers loaded plus SpringDoc configuration imported
 * to test the OpenAPI generation without requiring database or Redis.
 *
 * Validates: Requirements 6.2, 6.3
 */
@WebMvcTest(controllers = {WorkflowController.class, ApiConfigController.class})
@Import(OpenApiConfig.class)
@ImportAutoConfiguration({
    SpringDocConfiguration.class,
    SpringDocConfigProperties.class,
    SpringDocWebMvcConfiguration.class,
    SwaggerUiConfigProperties.class,
    SwaggerUiOAuthProperties.class,
    SpringDocUIConfiguration.class,
    SwaggerConfig.class
})
class OpenApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private WorkflowService workflowService;

    @MockBean
    private WorkflowCacheService workflowCacheService;

    @MockBean
    private ApiConfigService apiConfigService;

    @MockBean
    private ApiConfigCacheService apiConfigCacheService;

    @Test
    void apiDocsShouldReturnValidJsonWithExpectedPaths() throws Exception {
        // Act
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).isNotEmpty();

        JsonNode root = objectMapper.readTree(responseBody);

        // Verify OpenAPI 3.x structure
        assertThat(root.has("openapi")).isTrue();
        assertThat(root.get("openapi").asText()).startsWith("3.");

        // Verify info section matches OpenApiConfig bean
        JsonNode info = root.get("info");
        assertThat(info).isNotNull();
        assertThat(info.get("title").asText()).isEqualTo("Chatbot Workflow Engine API");
        assertThat(info.get("version").asText()).isEqualTo("1.0.0");

        // Verify paths section contains workflow endpoints
        JsonNode paths = root.get("paths");
        assertThat(paths).isNotNull();
        assertThat(paths.has("/api/workflows")).isTrue();
        assertThat(paths.has("/api/workflows/{id}")).isTrue();

        // Verify paths section contains api-config endpoints
        assertThat(paths.has("/api/api-configs")).isTrue();
        assertThat(paths.has("/api/api-configs/{id}")).isTrue();
    }

    @Test
    void swaggerUiShouldBeAccessibleWhenEnabled() throws Exception {
        // The /swagger-ui.html endpoint should redirect to the Swagger UI page
        // In a @WebMvcTest context, static webjar resources aren't served,
        // but the redirect endpoint registered by SpringDoc should respond
        MvcResult result = mockMvc.perform(get("/swagger-ui.html"))
                .andReturn();

        int statusCode = result.getResponse().getStatus();
        // SpringDoc registers /swagger-ui.html as a redirect to /swagger-ui/index.html
        assertThat(statusCode).isEqualTo(302);
        assertThat(result.getResponse().getRedirectedUrl()).contains("swagger-ui");
    }
}
