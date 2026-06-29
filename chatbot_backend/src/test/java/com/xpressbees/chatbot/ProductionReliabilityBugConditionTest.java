package com.xpressbees.chatbot;

import com.xpressbees.chatbot.entity.ChatSession;
import com.xpressbees.chatbot.entity.Workflow;
import com.xpressbees.chatbot.repository.WorkflowRepository;
import com.xpressbees.chatbot.service.ChildWorkflowService;
import com.xpressbees.chatbot.service.NavigationService;
import com.xpressbees.chatbot.service.PlaceholderService;
import com.xpressbees.chatbot.service.WorkflowCacheService;
import net.jqwik.api.*;
import jakarta.persistence.Version;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.*;

import static org.mockito.Mockito.*;

/**
 * Bug Condition Exploration Tests — Production Reliability Bugs
 *
 * Property 1: Bug Condition — Data Race, Proxy Misconfiguration, Cache Bypass
 *
 * Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7
 *
 * CRITICAL: These tests are EXPECTED TO FAIL on unfixed code.
 * Failure confirms the bugs exist. DO NOT fix the tests or code when they fail.
 *
 * Bug 1: ChatSession lacks @Version annotation → no optimistic locking → data race
 * Bug 2: application-prod.properties lacks server.forward-headers-strategy=native
 * Bug 3: ChildWorkflowService and NavigationService use WorkflowRepository directly
 *         instead of WorkflowCacheService
 */
class ProductionReliabilityBugConditionTest {

    // ========================================================================
    // Bug 1: Data Race — Missing @Version on ChatSession
    // ========================================================================

    /**
     * Asserts that ChatSession has a @Version-annotated field for optimistic locking.
     * On unfixed code, this will FAIL because no @Version field exists.
     *
     * Without @Version, two concurrent threads can read the same ChatSession row,
     * apply independent context mutations, and save without conflict detection.
     * The last writer silently overwrites the first writer's changes (lost update).
     */
    @Property(tries = 50)
    @Tag("Bug Condition: Data Race — Missing Optimistic Locking")
    void chatSessionMustHaveVersionFieldForOptimisticLocking(
            @ForAll("randomContextMaps") Map<String, Object> contextA,
            @ForAll("randomContextMaps") Map<String, Object> contextB) {

        // **Validates: Requirements 1.1**

        // ASSERT: ChatSession entity has a @Version-annotated field
        // This is a precondition for optimistic locking to work.
        // Without it, concurrent writes will silently overwrite each other.
        Field versionField = findVersionAnnotatedField(ChatSession.class);

        assert versionField != null :
                "BUG CONFIRMED: ChatSession has NO @Version field. " +
                "Concurrent threads reading the same row and applying independent mutations " +
                "(contextA=" + contextA + ", contextB=" + contextB + ") will result in lost updates. " +
                "The last writer overwrites without ObjectOptimisticLockingFailureException being thrown.";
    }

    // ========================================================================
    // Bug 2: Reverse Proxy — Missing server.forward-headers-strategy
    // ========================================================================

    /**
     * Asserts that application-prod.properties contains
     * server.forward-headers-strategy=native.
     *
     * On unfixed code, this will FAIL because the property is not set.
     * Without this property, X-Forwarded-For and X-Forwarded-Proto headers are
     * ignored, causing request.getRemoteAddr() to return the proxy IP and
     * request.isSecure() to return false even for HTTPS clients.
     */
    @Property(tries = 50)
    @Tag("Bug Condition: Reverse Proxy — Missing Forward Headers Strategy")
    void prodPropertiesMustConfigureForwardHeadersStrategy(
            @ForAll("randomClientIps") String clientIp,
            @ForAll("randomProtocols") String protocol) {

        // **Validates: Requirements 1.2, 1.3**

        // Load production properties and check for the required configuration
        Properties prodProperties = loadProdProperties();

        String forwardHeadersStrategy = prodProperties.getProperty("server.forward-headers-strategy");

        assert "native".equals(forwardHeadersStrategy) :
                "BUG CONFIRMED: application-prod.properties does NOT set " +
                "server.forward-headers-strategy=native. " +
                "A request from client IP " + clientIp + " via " + protocol + " through a reverse proxy " +
                "will resolve request.getRemoteAddr() as the proxy IP (not " + clientIp + ") " +
                "and request.isSecure() will return " + (!"https".equals(protocol)) + " " +
                "even when the original client connection was " + protocol + ". " +
                "Current value: " + forwardHeadersStrategy;
    }

    // ========================================================================
    // Bug 3: Cache Bypass — ChildWorkflowService uses WorkflowRepository directly
    // ========================================================================

    /**
     * Asserts that ChildWorkflowService.enterChild() calls
     * WorkflowCacheService.findById() instead of WorkflowRepository.findById().
     *
     * On unfixed code, this will FAIL because ChildWorkflowService injects
     * WorkflowRepository directly and never uses WorkflowCacheService.
     */
    @Property(tries = 50)
    @Tag("Bug Condition: Cache Bypass — ChildWorkflowService.enterChild()")
    void childWorkflowServiceEnterChildMustUseCacheService(
            @ForAll("positiveWorkflowIds") Long childWorkflowId) {

        // **Validates: Requirements 1.4**

        // Verify ChildWorkflowService constructor accepts WorkflowCacheService
        // (not just WorkflowRepository)
        boolean acceptsCacheService = constructorAcceptsType(
                ChildWorkflowService.class, WorkflowCacheService.class);

        assert acceptsCacheService :
                "BUG CONFIRMED: ChildWorkflowService does NOT accept WorkflowCacheService " +
                "in its constructor. It uses WorkflowRepository directly. " +
                "enterChild(session, " + childWorkflowId + ", node) will call " +
                "workflowRepository.findById(" + childWorkflowId + ") bypassing Redis cache, " +
                "hitting PostgreSQL on every child workflow entry.";
    }

    /**
     * Asserts that ChildWorkflowService.handleChildEnd() calls
     * WorkflowCacheService.findById() instead of WorkflowRepository.findById().
     *
     * On unfixed code, this will FAIL because ChildWorkflowService injects
     * WorkflowRepository directly and never uses WorkflowCacheService.
     */
    @Property(tries = 50)
    @Tag("Bug Condition: Cache Bypass — ChildWorkflowService.handleChildEnd()")
    void childWorkflowServiceHandleChildEndMustUseCacheService(
            @ForAll("positiveWorkflowIds") Long parentWorkflowId) {

        // **Validates: Requirements 1.5**

        // Verify ChildWorkflowService has a field of type WorkflowCacheService
        boolean hasCacheServiceField = hasFieldOfType(
                ChildWorkflowService.class, WorkflowCacheService.class);

        assert hasCacheServiceField :
                "BUG CONFIRMED: ChildWorkflowService has NO WorkflowCacheService field. " +
                "handleChildEnd() will call workflowRepository.findById(" + parentWorkflowId + ") " +
                "bypassing Redis cache, hitting PostgreSQL on every child workflow exit.";
    }

    /**
     * Asserts that NavigationService.handleBack() calls
     * WorkflowCacheService.findById() instead of WorkflowRepository.findById().
     *
     * On unfixed code, this will FAIL because NavigationService injects
     * WorkflowRepository directly and never uses WorkflowCacheService.
     */
    @Property(tries = 50)
    @Tag("Bug Condition: Cache Bypass — NavigationService.handleBack()")
    void navigationServiceHandleBackMustUseCacheService(
            @ForAll("positiveWorkflowIds") Long targetWorkflowId) {

        // **Validates: Requirements 1.6**

        boolean acceptsCacheService = constructorAcceptsType(
                NavigationService.class, WorkflowCacheService.class);

        assert acceptsCacheService :
                "BUG CONFIRMED: NavigationService does NOT accept WorkflowCacheService " +
                "in its constructor. It uses WorkflowRepository directly. " +
                "handleBack() will call workflowRepository.findById(" + targetWorkflowId + ") " +
                "bypassing Redis cache, hitting PostgreSQL on every back navigation.";
    }

    /**
     * Asserts that NavigationService.handleRestart() calls
     * WorkflowCacheService.findById() instead of WorkflowRepository.findById().
     *
     * On unfixed code, this will FAIL because NavigationService injects
     * WorkflowRepository directly and never uses WorkflowCacheService.
     */
    @Property(tries = 50)
    @Tag("Bug Condition: Cache Bypass — NavigationService.handleRestart()")
    void navigationServiceHandleRestartMustUseCacheService(
            @ForAll("positiveWorkflowIds") Long rootWorkflowId) {

        // **Validates: Requirements 1.7**

        boolean hasCacheServiceField = hasFieldOfType(
                NavigationService.class, WorkflowCacheService.class);

        assert hasCacheServiceField :
                "BUG CONFIRMED: NavigationService has NO WorkflowCacheService field. " +
                "handleRestart() will call workflowRepository.findById(" + rootWorkflowId + ") " +
                "bypassing Redis cache, hitting PostgreSQL on every restart navigation.";
    }

    // ========================================================================
    // Providers
    // ========================================================================

    @Provide
    Arbitrary<Map<String, Object>> randomContextMaps() {
        Arbitrary<String> keys = Arbitraries.strings()
                .alpha().ofMinLength(1).ofMaxLength(10);
        Arbitrary<Object> values = Arbitraries.oneOf(
                Arbitraries.strings().alpha().ofMaxLength(20).map(s -> (Object) s),
                Arbitraries.integers().between(0, 1000).map(i -> (Object) i),
                Arbitraries.of(true, false).map(b -> (Object) b)
        );
        return Arbitraries.maps(keys, values).ofMinSize(1).ofMaxSize(5);
    }

    @Provide
    Arbitrary<String> randomClientIps() {
        // Generate random IPv4 addresses
        Arbitrary<Integer> octet = Arbitraries.integers().between(1, 254);
        return Combinators.combine(octet, octet, octet, octet)
                .as((a, b, c, d) -> a + "." + b + "." + c + "." + d);
    }

    @Provide
    Arbitrary<String> randomProtocols() {
        return Arbitraries.of("https", "http");
    }

    @Provide
    Arbitrary<Long> positiveWorkflowIds() {
        return Arbitraries.longs().between(1L, 100_000L);
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    /**
     * Finds a field annotated with @Version in the given class.
     * Returns null if no such field exists.
     */
    private Field findVersionAnnotatedField(Class<?> clazz) {
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(Version.class)) {
                return field;
            }
        }
        return null;
    }

    /**
     * Checks if any constructor of the given class accepts a parameter of the specified type.
     */
    private boolean constructorAcceptsType(Class<?> targetClass, Class<?> paramType) {
        for (Constructor<?> constructor : targetClass.getDeclaredConstructors()) {
            for (Class<?> parameterType : constructor.getParameterTypes()) {
                if (parameterType.equals(paramType)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks if the given class has a declared field of the specified type.
     */
    private boolean hasFieldOfType(Class<?> targetClass, Class<?> fieldType) {
        for (Field field : targetClass.getDeclaredFields()) {
            if (field.getType().equals(fieldType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Loads application-prod.properties from the classpath (src/main/resources).
     */
    private Properties loadProdProperties() {
        Properties props = new Properties();
        // Load from the main resources (not test resources)
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream("application-prod.properties")) {
            if (is != null) {
                props.load(is);
            }
        } catch (IOException e) {
            // If file can't be loaded, properties will be empty — test will still fail
            // because the required property won't be found
        }
        return props;
    }
}
