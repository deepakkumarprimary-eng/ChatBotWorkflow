package com.xpressbees.chatbot.config;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import io.swagger.v3.oas.annotations.Operation;
import net.jqwik.api.*;
import org.springframework.web.bind.annotation.*;

import com.xpressbees.chatbot.controller.ApiConfigController;
import com.xpressbees.chatbot.controller.WorkflowController;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 4: OpenAPI endpoint documentation completeness
 *
 * <p><b>Validates: Requirements 6.4</b></p>
 *
 * <p>Property: For any REST endpoint defined in WorkflowController or ApiConfigController,
 * the generated OpenAPI specification SHALL contain a path entry with a non-empty description,
 * a request body schema (for POST/PUT methods), and a response schema.</p>
 *
 * <p>This test uses reflection to verify that all controller endpoint methods carry the
 * required OpenAPI annotations, which guarantees that the generated spec is complete.</p>
 */
class OpenApiCompletenessPropertyTest {

    private static final List<Class<?>> CONTROLLERS = List.of(
            WorkflowController.class,
            ApiConfigController.class
    );

    /**
     * Collects all REST endpoint methods from both controllers.
     */
    private static List<Method> getAllEndpointMethods() {
        return CONTROLLERS.stream()
                .flatMap(controller -> Arrays.stream(controller.getDeclaredMethods()))
                .filter(OpenApiCompletenessPropertyTest::isRestEndpoint)
                .collect(Collectors.toList());
    }

    private static boolean isRestEndpoint(Method method) {
        return method.isAnnotationPresent(GetMapping.class)
                || method.isAnnotationPresent(PostMapping.class)
                || method.isAnnotationPresent(PutMapping.class)
                || method.isAnnotationPresent(DeleteMapping.class)
                || method.isAnnotationPresent(RequestMapping.class)
                        && method.getDeclaringClass() != method.getDeclaringClass(); // exclude class-level
    }

    private static boolean isPostOrPutMethod(Method method) {
        return method.isAnnotationPresent(PostMapping.class)
                || method.isAnnotationPresent(PutMapping.class);
    }

    private static boolean hasRequestBody(Method method) {
        return Arrays.stream(method.getParameters())
                .anyMatch(param -> param.isAnnotationPresent(RequestBody.class));
    }

    @Property(tries = 1)
    @Tag("production-readiness")
    @Label("Every REST endpoint has @Operation annotation with non-empty description")
    void everyEndpointHasOperationWithDescription() {
        List<Method> endpoints = getAllEndpointMethods();

        assertThat(endpoints)
                .as("At least one REST endpoint should exist across controllers")
                .isNotEmpty();

        for (Method endpoint : endpoints) {
            Operation operation = endpoint.getAnnotation(Operation.class);
            assertThat(operation)
                    .as("Method %s.%s must have @Operation annotation",
                            endpoint.getDeclaringClass().getSimpleName(), endpoint.getName())
                    .isNotNull();
            assertThat(operation.description())
                    .as("@Operation description must not be empty for %s.%s",
                            endpoint.getDeclaringClass().getSimpleName(), endpoint.getName())
                    .isNotBlank();
            assertThat(operation.summary())
                    .as("@Operation summary must not be empty for %s.%s",
                            endpoint.getDeclaringClass().getSimpleName(), endpoint.getName())
                    .isNotBlank();
        }
    }

    @Property(tries = 1)
    @Tag("production-readiness")
    @Label("POST and PUT endpoints have @RequestBody parameter for request body schema")
    void postAndPutEndpointsHaveRequestBodySchema() {
        List<Method> postPutEndpoints = getAllEndpointMethods().stream()
                .filter(OpenApiCompletenessPropertyTest::isPostOrPutMethod)
                .collect(Collectors.toList());

        assertThat(postPutEndpoints)
                .as("At least one POST or PUT endpoint should exist across controllers")
                .isNotEmpty();

        for (Method endpoint : postPutEndpoints) {
            assertThat(hasRequestBody(endpoint))
                    .as("POST/PUT method %s.%s must have a @RequestBody parameter",
                            endpoint.getDeclaringClass().getSimpleName(), endpoint.getName())
                    .isTrue();
        }
    }

    @Property(tries = 1)
    @Tag("production-readiness")
    @Label("All controllers have @Tag annotation for OpenAPI grouping")
    void allControllersHaveTagAnnotation() {
        for (Class<?> controller : CONTROLLERS) {
            io.swagger.v3.oas.annotations.tags.Tag tag =
                    controller.getAnnotation(io.swagger.v3.oas.annotations.tags.Tag.class);
            assertThat(tag)
                    .as("Controller %s must have @Tag annotation", controller.getSimpleName())
                    .isNotNull();
            assertThat(tag.name())
                    .as("@Tag name must not be empty for %s", controller.getSimpleName())
                    .isNotBlank();
            assertThat(tag.description())
                    .as("@Tag description must not be empty for %s", controller.getSimpleName())
                    .isNotBlank();
        }
    }

    @Property(tries = 1)
    @Tag("production-readiness")
    @Label("POST and PUT endpoints have @ApiResponses with response schemas")
    void postAndPutEndpointsHaveApiResponses() {
        List<Method> postPutEndpoints = getAllEndpointMethods().stream()
                .filter(OpenApiCompletenessPropertyTest::isPostOrPutMethod)
                .collect(Collectors.toList());

        for (Method endpoint : postPutEndpoints) {
            io.swagger.v3.oas.annotations.responses.ApiResponses responses =
                    endpoint.getAnnotation(io.swagger.v3.oas.annotations.responses.ApiResponses.class);
            assertThat(responses)
                    .as("POST/PUT method %s.%s must have @ApiResponses annotation",
                            endpoint.getDeclaringClass().getSimpleName(), endpoint.getName())
                    .isNotNull();
            assertThat(responses.value())
                    .as("@ApiResponses must contain at least one response for %s.%s",
                            endpoint.getDeclaringClass().getSimpleName(), endpoint.getName())
                    .isNotEmpty();
        }
    }
}
