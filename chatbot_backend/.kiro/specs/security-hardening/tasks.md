# Implementation Plan: Security Hardening

## Overview

This plan implements seven security hardening measures across the Chatbot Workflow Engine backend. Tasks are ordered to build foundational components first (encryption, configuration), then layer on validation and protection mechanisms, and finally wire everything together with containerization.

## Tasks

- [x] 1. Set up dependencies and profile-based configuration
  - [x] 1.1 Add required Maven dependencies to pom.xml
    - Add `spring-boot-starter-actuator` for health endpoint
    - Add `spring-boot-starter-validation` for Jakarta Bean Validation
    - Verify `spring-boot-starter-websocket` and `jqwik` are already present
    - _Requirements: 4.1, 7.10_

  - [x] 1.2 Create profile-specific property files
    - Create `application-dev.properties` with local dev database, permissive CORS (`http://localhost:3000`), local encryption key, and dev WebSocket API key
    - Create `application-staging.properties` with staging-specific values for all properties
    - Create `application-prod.properties` with `${ENV_VAR}` placeholders for all sensitive values (no defaults, so missing vars cause startup failure)
    - _Requirements: 6.1, 6.4, 6.5, 6.6, 6.7, 6.8, 6.9_

  - [x] 1.3 Refactor base application.properties
    - Move sensitive/environment-specific values out of the base file
    - Set `spring.profiles.default=dev` for safe default behavior
    - Keep only shared, non-sensitive defaults (server port, JPA settings, Hibernate ddl-auto, cache TTL)
    - Expose actuator health endpoint only: `management.endpoints.web.exposure.include=health`
    - _Requirements: 6.2, 6.3, 6.4_

- [x] 2. Implement encryption at rest
  - [x] 2.1 Create EncryptionService class
    - Implement `com.xpressbees.chatbot.service.EncryptionService` with AES-256-GCM encryption
    - Load encryption key from `${encryption.key}` property (Base64-encoded)
    - Generate random 12-byte IV per encryption call, prepend to ciphertext
    - Throw `IllegalStateException` at startup if key is missing or malformed
    - Handle null inputs by returning null (pass-through for optional fields)
    - _Requirements: 1.1, 1.2, 1.3, 1.4_

  - [x] 2.2 Create custom EncryptionException class
    - Add `com.xpressbees.chatbot.exception.EncryptionException` runtime exception
    - Include ApiConfig identifier in message for traceability on decryption failures
    - _Requirements: 1.5_

  - [x] 2.3 Create ApiConfigEntityListener with JPA lifecycle callbacks
    - Implement `@PrePersist` and `@PreUpdate` to encrypt `username` and `password` fields
    - Implement `@PostLoad` to decrypt `username` and `password` fields
    - Use a Spring `ApplicationContext` static holder pattern to obtain `EncryptionService` (JPA listeners are not Spring-managed)
    - _Requirements: 1.1, 1.2_

  - [x] 2.4 Register entity listener on ApiConfig entity
    - Add `@EntityListeners(ApiConfigEntityListener.class)` to the `ApiConfig` entity class
    - No database schema changes required (columns store Base64-encoded ciphertext)
    - _Requirements: 1.1, 1.2_

  - [x]* 2.5 Write property test for encryption round-trip (Property 1)
    - **Property 1: Encryption round-trip preserves plaintext**
    - Create `EncryptionServicePropertyTest` using jqwik
    - Generate arbitrary strings (including empty, unicode, up to 255 chars)
    - Assert `decrypt(encrypt(plaintext)) == plaintext` for all generated inputs
    - **Validates: Requirements 1.1, 1.2, 1.6**

  - [x]* 2.6 Write unit tests for EncryptionService
    - Test startup failure when key is missing
    - Test startup failure with invalid Base64 key
    - Test decryption failure with corrupted ciphertext throws EncryptionException
    - Test null input handling
    - _Requirements: 1.3, 1.4, 1.5_

- [x] 3. Implement centralized CORS configuration
  - [x] 3.1 Create CorsConfig class
    - Implement `com.xpressbees.chatbot.config.CorsConfig` implementing `WebMvcConfigurer`
    - Load allowed origins from `${cors.allowed-origins}` property with default `http://localhost:3000`
    - Register CORS mapping for `/**` with allowed methods GET, POST, PUT, DELETE, OPTIONS
    - Set `allowCredentials(true)` and `allowedHeaders("*")`
    - _Requirements: 2.1, 2.3, 2.4_

  - [x] 3.2 Update WebSocketConfig to use centralized CORS origins
    - Inject `${cors.allowed-origins}` into `WebSocketConfig`
    - Use the same allowed origins for WebSocket handshake `setAllowedOrigins()`
    - _Requirements: 2.2_

  - [x] 3.3 Remove all @CrossOrigin annotations from controllers
    - Remove `@CrossOrigin` from `ApiConfigController`
    - Remove `@CrossOrigin` from `ChatWebSocketController`
    - Remove any other `@CrossOrigin` annotations found in the codebase
    - _Requirements: 2.5_

  - [x]* 3.4 Write integration tests for CORS configuration
    - Test that requests with valid Origin header are accepted
    - Test that requests with invalid Origin header receive 403
    - Test that requests without Origin header are rejected
    - Test default origins when no profile-specific value is set
    - _Requirements: 2.1, 2.6, 2.7_

- [x] 4. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. Implement SSRF protection
  - [x] 5.1 Create ValidationResult record
    - Implement `com.xpressbees.chatbot.dto.ValidationResult` record with `boolean allowed` and `String reason`
    - Add static factory methods: `ValidationResult.allowed()` and `ValidationResult.blocked(String reason)`
    - _Requirements: 3.5_

  - [x] 5.2 Create UrlValidator component
    - Implement `com.xpressbees.chatbot.service.UrlValidator` annotated with `@Component`
    - Load `${ssrf.allowed-hosts}` property as a list of host patterns
    - Implement `validate(String url)` method returning `ValidationResult`
    - Implement `isPrivateIp(InetAddress)` checking 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16, 127.0.0.0/8, 169.254.0.0/16, and IPv6 loopback
    - Implement `isAllowedScheme(String)` permitting only `http` and `https`
    - Implement `matchesAllowlist(String host)` with glob/pattern matching
    - Perform DNS resolution and check resolved IP against private ranges (DNS rebinding protection)
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.6_

  - [x] 5.3 Integrate UrlValidator into PlaceholderService and HttpExecutor
    - Call `UrlValidator.validate()` after placeholder resolution in the API node processing flow
    - Add a second validation check in `HttpExecutor` before executing any request (defense in depth)
    - Return `HttpExecutionResult` with error message when URL is blocked
    - _Requirements: 3.1, 3.5, 3.7_

  - [x]* 5.4 Write property tests for SSRF protection (Properties 2, 3, 4)
    - **Property 2: Private IP addresses are always rejected**
    - **Property 3: Non-HTTP schemes are always rejected**
    - **Property 4: Allowlist enforcement blocks non-matching hosts**
    - Create `UrlValidatorPropertyTest` using jqwik
    - Generate arbitrary private IPs and assert all are blocked
    - Generate arbitrary non-http/https schemes and assert all are blocked
    - Generate hosts not in a configured allowlist and assert all are blocked
    - **Validates: Requirements 3.2, 3.3, 3.4**

  - [x]* 5.5 Write unit tests for UrlValidator
    - Test specific private IP examples (10.0.0.1, 172.16.0.1, 192.168.1.1, 127.0.0.1)
    - Test DNS rebinding scenario with mocked DNS resolution
    - Test allowlist matching edge cases (wildcard patterns, exact match)
    - Test DNS resolution failure handling
    - _Requirements: 3.2, 3.4, 3.6_

- [x] 6. Implement input validation on DTOs
  - [x] 6.1 Add Jakarta validation annotations to request DTOs
    - `ApiConfigRequest`: Add `@NotBlank` on `name`, `url`, `method`; `@Size(max=255)` on `name`; `@Size(max=1024)` on `url`
    - `WorkflowRequest`: Add `@NotBlank` on `name`; `@NotNull` on `workflowJson`; `@Size(max=255)` on `name`
    - `ChatStartRequest`: Add `@NotBlank` on `sessionId`
    - `ChatMessageRequest`: Add `@NotBlank` on `sessionId`
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_

  - [x] 6.2 Add @Valid annotations to controller method parameters
    - Add `@Valid` to all `@RequestBody` parameters in `ApiConfigController`
    - Add `@Valid` to all `@RequestBody` parameters in `WorkflowController`
    - Add `@Valid` to WebSocket message handler parameters where applicable
    - _Requirements: 4.7_

  - [x] 6.3 Add validation error handler to GlobalExceptionHandler
    - Add `@ExceptionHandler(MethodArgumentNotValidException.class)` method
    - Return HTTP 400 with JSON body: `{"error": "Validation failed", "violations": [{"field": "...", "message": "..."}]}`
    - _Requirements: 4.6_

  - [x]* 6.4 Write property tests for input validation (Properties 5, 6)
    - **Property 5: Whitespace-only strings are rejected by NotBlank validation**
    - **Property 6: Strings exceeding maximum length are rejected by Size validation**
    - Create `InputValidationPropertyTest` using jqwik
    - Generate arbitrary whitespace-only strings and validate against `@NotBlank`-annotated fields
    - Generate strings exceeding max length and validate against `@Size`-annotated fields
    - **Validates: Requirements 4.1, 4.2, 4.4, 4.5**

  - [x]* 6.5 Write unit tests for validation error responses
    - Test that validation failure returns HTTP 400 with proper violation format
    - Test multiple simultaneous violations
    - _Requirements: 4.6_

- [x] 7. Implement WebSocket authentication
  - [x] 7.1 Create WebSocketAuthInterceptor
    - Implement `com.xpressbees.chatbot.config.WebSocketAuthInterceptor` implementing `ChannelInterceptor`
    - Load valid API key from `${websocket.api-key}` property
    - Fail application startup if `WEBSOCKET_API_KEY` env var is unset
    - Override `preSend()` to intercept STOMP CONNECT frames
    - Extract API key from `X-API-Key` native header or `apiKey` query parameter
    - Use `MessageDigest.isEqual()` for constant-time comparison
    - Reject invalid/missing keys with STOMP ERROR frame
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6_

  - [x] 7.2 Register interceptor in WebSocketConfig
    - Configure the inbound client channel interceptor in `WebSocketConfig`
    - Add `configureClientInboundChannel()` override to register `WebSocketAuthInterceptor`
    - _Requirements: 5.1_

  - [x]* 7.3 Write unit tests for WebSocketAuthInterceptor
    - Test valid API key allows connection
    - Test invalid API key rejects connection with STOMP ERROR
    - Test missing API key rejects connection
    - Test constant-time comparison behavior
    - _Requirements: 5.1, 5.2, 5.6_

- [x] 8. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 9. Docker containerization
  - [x] 9.1 Create multi-stage Dockerfile
    - Build stage: Use `maven:3.9-eclipse-temurin-17` base image, run `mvn package -DskipTests`
    - Runtime stage: Use `eclipse-temurin:17-jre-alpine` base image
    - Copy only the packaged JAR from build stage
    - Create non-root user `appuser` with `adduser --disabled-password --no-create-home appuser`
    - Switch to `appuser` with `USER appuser`
    - Expose port 8080
    - Add health check: `HEALTHCHECK CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1`
    - Minimize layers by combining related RUN commands
    - Accept environment variables: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `ENCRYPTION_KEY`, `WEBSOCKET_API_KEY`, `SPRING_PROFILES_ACTIVE`
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7, 7.8, 7.10_

  - [x] 9.2 Create .dockerignore file
    - Exclude: `.git`, `target/`, `.idea/`, `*.iml`, `.kiro/`, `.vscode/`, `.cursor/`, `*.log`, `docs/`
    - _Requirements: 7.9_

- [x] 10. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document (Properties 1–6)
- Unit tests validate specific examples, error paths, and edge cases
- The project already includes jqwik 1.8.2 in `pom.xml` — no additional test framework setup needed
- Java 17, Spring Boot 3.3.5, Maven, PostgreSQL, Lombok conventions are used throughout
- Constructor injection is used per project conventions (no `@Autowired` field injection)

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2"] },
    { "id": 1, "tasks": ["1.3"] },
    { "id": 2, "tasks": ["2.1", "2.2", "3.1", "5.1", "6.1"] },
    { "id": 3, "tasks": ["2.3", "3.2", "3.3", "5.2", "6.2", "6.3", "7.1"] },
    { "id": 4, "tasks": ["2.4", "5.3", "7.2"] },
    { "id": 5, "tasks": ["2.5", "2.6", "3.4", "5.4", "5.5", "6.4", "6.5", "7.3"] },
    { "id": 6, "tasks": ["9.1", "9.2"] }
  ]
}
```
