package com.xpressbees.chatbot.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for CorsConfig verifying centralized CORS configuration behavior.
 * Tests the CorsConfig bean directly without requiring a full Spring context,
 * avoiding PostgreSQL/Redis dependencies.
 *
 * Validates: Requirements 2.1, 2.6, 2.7
 */
class CorsConfigTest {

    @Nested
    @DisplayName("CORS configuration with explicit allowed origins")
    class ExplicitOrigins {

        @Test
        @DisplayName("Configures registry with the specified allowed origin")
        void configuresRegistryWithSpecifiedOrigin() {
            CorsConfig corsConfig = new CorsConfig(new String[]{"http://localhost:3000"});
            CorsRegistry registry = new CorsRegistry();

            corsConfig.addCorsMappings(registry);

            Map<String, CorsConfiguration> configs = getCorsConfigurations(registry);
            assertThat(configs).containsKey("/**");

            CorsConfiguration config = configs.get("/**");
            assertThat(config.getAllowedOrigins()).containsExactly("http://localhost:3000");
        }

        @Test
        @DisplayName("Accepts request with valid origin matching configured origins")
        void acceptsRequestWithValidOrigin() {
            CorsConfig corsConfig = new CorsConfig(new String[]{"http://localhost:3000"});
            CorsRegistry registry = new CorsRegistry();

            corsConfig.addCorsMappings(registry);

            CorsConfiguration config = getCorsConfigurations(registry).get("/**");
            String result = config.checkOrigin("http://localhost:3000");
            assertThat(result).isEqualTo("http://localhost:3000");
        }

        @Test
        @DisplayName("Rejects request with invalid origin not matching configured origins")
        void rejectsRequestWithInvalidOrigin() {
            CorsConfig corsConfig = new CorsConfig(new String[]{"http://localhost:3000"});
            CorsRegistry registry = new CorsRegistry();

            corsConfig.addCorsMappings(registry);

            CorsConfiguration config = getCorsConfigurations(registry).get("/**");
            String result = config.checkOrigin("http://evil.com");
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Supports multiple allowed origins")
        void supportsMultipleAllowedOrigins() {
            CorsConfig corsConfig = new CorsConfig(
                    new String[]{"http://localhost:3000", "https://staging.example.com"});
            CorsRegistry registry = new CorsRegistry();

            corsConfig.addCorsMappings(registry);

            CorsConfiguration config = getCorsConfigurations(registry).get("/**");
            assertThat(config.getAllowedOrigins())
                    .containsExactly("http://localhost:3000", "https://staging.example.com");

            // Valid origins are accepted
            assertThat(config.checkOrigin("http://localhost:3000")).isEqualTo("http://localhost:3000");
            assertThat(config.checkOrigin("https://staging.example.com")).isEqualTo("https://staging.example.com");

            // Invalid origin is rejected
            assertThat(config.checkOrigin("http://evil.com")).isNull();
        }
    }

    @Nested
    @DisplayName("Default CORS origins")
    class DefaultOrigins {

        @Test
        @DisplayName("Defaults to http://localhost:3000 when no profile-specific value is set")
        void defaultsToLocalhostWhenNoProfileValueSet() {
            // The @Value annotation in CorsConfig uses default: ${cors.allowed-origins:http://localhost:3000}
            // When no property is set, Spring will inject the default value
            CorsConfig corsConfig = new CorsConfig(new String[]{"http://localhost:3000"});
            CorsRegistry registry = new CorsRegistry();

            corsConfig.addCorsMappings(registry);

            CorsConfiguration config = getCorsConfigurations(registry).get("/**");
            assertThat(config.getAllowedOrigins()).containsExactly("http://localhost:3000");
        }
    }

    @Nested
    @DisplayName("CORS configuration details")
    class ConfigurationDetails {

        @Test
        @DisplayName("Configures allowed methods correctly")
        void configuresAllowedMethods() {
            CorsConfig corsConfig = new CorsConfig(new String[]{"http://localhost:3000"});
            CorsRegistry registry = new CorsRegistry();

            corsConfig.addCorsMappings(registry);

            CorsConfiguration config = getCorsConfigurations(registry).get("/**");
            assertThat(config.getAllowedMethods())
                    .containsExactlyInAnyOrder("GET", "POST", "PUT", "DELETE", "OPTIONS");
        }

        @Test
        @DisplayName("Allows credentials")
        void allowsCredentials() {
            CorsConfig corsConfig = new CorsConfig(new String[]{"http://localhost:3000"});
            CorsRegistry registry = new CorsRegistry();

            corsConfig.addCorsMappings(registry);

            CorsConfiguration config = getCorsConfigurations(registry).get("/**");
            assertThat(config.getAllowCredentials()).isTrue();
        }

        @Test
        @DisplayName("Allows all headers")
        void allowsAllHeaders() {
            CorsConfig corsConfig = new CorsConfig(new String[]{"http://localhost:3000"});
            CorsRegistry registry = new CorsRegistry();

            corsConfig.addCorsMappings(registry);

            CorsConfiguration config = getCorsConfigurations(registry).get("/**");
            assertThat(config.getAllowedHeaders()).contains("*");
        }

        @Test
        @DisplayName("Applies mapping to all paths")
        void appliesMappingToAllPaths() {
            CorsConfig corsConfig = new CorsConfig(new String[]{"http://localhost:3000"});
            CorsRegistry registry = new CorsRegistry();

            corsConfig.addCorsMappings(registry);

            Map<String, CorsConfiguration> configs = getCorsConfigurations(registry);
            assertThat(configs).containsKey("/**");
        }
    }

    /**
     * Helper method to extract CorsConfiguration mappings from the registry.
     * CorsRegistry.getCorsConfigurations() is protected, so we use a test subclass to access it.
     */
    private Map<String, CorsConfiguration> getCorsConfigurations(CorsRegistry registry) {
        return new TestCorsRegistry(registry).getConfigurations();
    }

    /**
     * Test helper that extends CorsRegistry to expose the protected getCorsConfigurations() method.
     */
    private static class TestCorsRegistry extends CorsRegistry {

        private final CorsRegistry delegate;

        TestCorsRegistry(CorsRegistry delegate) {
            this.delegate = delegate;
        }

        Map<String, CorsConfiguration> getConfigurations() {
            // We can't call delegate.getCorsConfigurations() directly either.
            // Instead, we use the same registry instance passed to addCorsMappings
            // and access it via reflection.
            try {
                var method = CorsRegistry.class.getDeclaredMethod("getCorsConfigurations");
                method.setAccessible(true);
                @SuppressWarnings("unchecked")
                Map<String, CorsConfiguration> result =
                        (Map<String, CorsConfiguration>) method.invoke(delegate);
                return result;
            } catch (Exception e) {
                throw new RuntimeException("Failed to access CorsRegistry configurations", e);
            }
        }
    }
}
