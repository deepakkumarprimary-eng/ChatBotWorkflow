# Design Document: Security Hardening

## Overview

This design addresses seven security hardening measures for the Chatbot Workflow Engine backend. The implementation follows a layered approach that integrates with the existing Spring Boot architecture (Controller → Service → Repository → Entity) using constructor injection, Lombok, and Spring configuration conventions already established in the project.

The hardening covers:
1. **Encryption at Rest** — AES-256 encryption of sensitive fields in the `ApiConfig` entity using JPA lifecycle callbacks
2. **CORS Centralization** — Single `WebMvcConfigurer` bean replacing scattered `@CrossOrigin` annotations
3. **SSRF Protection** — URL validation service that checks resolved URLs against allowlists and private IP ranges before HTTP execution
4. **Input Validation** — Jakarta Bean Validation annotations on request DTOs with a centralized validation error handler
5. **WebSocket Authentication** — STOMP channel interceptor that validates API keys on connection
6. **Profile-Based Configuration** — Environment-specific property files for dev, staging, and production
7. **Docker Containerization** — Multi-stage Dockerfile with security best practices

## Architecture

```mermaid
graph TD
    subgraph "Request Flow"
        Client[Client] -->|REST/WS| CORS[CorsConfiguration]
        CORS --> Controller[Controllers]
        Controller -->|@Valid| InputValidation[Jakarta Validation]
        InputValidation --> Service[Service Layer]
    end

    subgraph "WebSocket Flow"
        WSClient[WS Client] -->|STOMP CONNECT| AuthInterceptor[WebSocketAuthInterceptor]
        AuthInterceptor -->|Valid API Key| StompBroker[STOMP Broker]
        AuthInterceptor -->|Invalid| Reject[Connection Rejected]
    end

    subgraph "Outbound HTTP Flow"
        Service --> PlaceholderService[PlaceholderService]
        PlaceholderService -->|Resolved URL| UrlValidator[UrlValidator]
        UrlValidator -->|Allowed| HttpExecutor[HttpExecutor]
        UrlValidator -->|Blocked| BlockedError[SSRF Block Error]
        HttpExecutor -->|Double-check| UrlValidator2[UrlValidator]
    end

    subgraph "Data Layer"
        Service --> Repository[JPA Repository]
        Repository -->|@PrePersist| EncryptionService[EncryptionService]
        Repository -->|@PostLoad| EncryptionService
        EncryptionService --> DB[(PostgreSQL)]
    end

    subgraph "Configuration"
        Profiles[Spring Profiles] -->|dev/staging/prod| AppConfig[application-{profile}.properties]
        EnvVars[Environment Variables] --> AppConfig
    end
```

## Components and Interfaces

### 1. EncryptionService

**Package:** `com.xpressbees.chatbot.service`

**Responsibility:** Encrypts and decrypts sensitive string fields using AES-256-GCM symmetric encryption.

```java
@Service
public class EncryptionService {

    private final SecretKey secretKey;

    public EncryptionService(@Value("${encryption.key}") String base64Key) {
        // Decode base64 key into AES SecretKey
        // Fail fast with descriptive error if key is missing/invalid
    }

    public String encrypt(String plaintext) { /* AES-256-GCM encrypt, return Base64 */ }
    public String decrypt(String ciphertext) { /* AES-256-GCM decrypt, return plaintext */ }
}
```

**Design Decisions:**
- Uses AES-256-GCM (authenticated encryption) rather than AES-CBC to prevent padding oracle attacks and ensure integrity.
- Each encryption call generates a random 12-byte IV prepended to the ciphertext.
- Key is loaded from `${encryption.key}` property, which resolves to `${ENCRYPTION_KEY}` env var in production.
- Throws `IllegalStateException` at startup if the key is missing or malformed.

### 2. ApiConfigEntityListener

**Package:** `com.xpressbees.chatbot.entity`

**Responsibility:** JPA entity listener that hooks into `@PrePersist`, `@PreUpdate`, and `@PostLoad` to transparently encrypt/decrypt `username` and `password` fields on the `ApiConfig` entity.

```java
public class ApiConfigEntityListener {

    @PrePersist
    @PreUpdate
    public void encryptFields(ApiConfig entity) { /* encrypt username, password */ }

    @PostLoad
    public void decryptFields(ApiConfig entity) { /* decrypt username, password */ }
}
```

**Design Decisions:**
- Uses `@EntityListeners` annotation on `ApiConfig` rather than an `AttributeConverter` to keep encryption logic centralized and testable.
- Obtains `EncryptionService` from the Spring `ApplicationContext` via a static holder pattern (JPA entity listeners are not Spring-managed beans by default).

### 3. CorsConfiguration

**Package:** `com.xpressbees.chatbot.config`

**Responsibility:** Centralized CORS policy applied globally to all REST and WebSocket endpoints.

```java
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${cors.allowed-origins:http://localhost:3000}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
```

**Design Decisions:**
- Replaces all `@CrossOrigin("*")` annotations currently on `ApiConfigController` and `ChatWebSocketController`.
- Defaults to `http://localhost:3000` (dev frontend) when no profile-specific value is set.
- WebSocket endpoint CORS is also locked down by referencing the same `cors.allowed-origins` property in `WebSocketConfig`.

### 4. UrlValidator

**Package:** `com.xpressbees.chatbot.service`

**Responsibility:** Validates resolved URLs against private IP ranges and an optional host allowlist before outbound HTTP execution.

```java
@Component
public class UrlValidator {

    private final List<String> allowedHostPatterns;

    public UrlValidator(
        @Value("${ssrf.allowed-hosts:}") List<String> allowedHostPatterns) { }

    public ValidationResult validate(String url) { /* returns allowed/blocked with reason */ }

    boolean isPrivateIp(InetAddress address) { /* checks RFC1918, loopback, link-local */ }
    boolean isAllowedScheme(String scheme) { /* only http/https */ }
    boolean matchesAllowlist(String host) { /* glob/pattern matching against allowed-hosts */ }
}
```

**Design Decisions:**
- Performs DNS resolution and checks the resolved IP against private ranges (10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16, 127.0.0.0/8, 169.254.0.0/16, ::1) to prevent DNS rebinding.
- When `ssrf.allowed-hosts` is configured, only requests to matching hosts pass. When not configured, all non-private hosts pass.
- Returns a `ValidationResult` record with `allowed` boolean and `reason` string.
- `HttpExecutor` performs its own validation call (defense in depth) before every request regardless of upstream validation.

### 5. WebSocketAuthInterceptor

**Package:** `com.xpressbees.chatbot.config`

**Responsibility:** STOMP channel interceptor that authenticates WebSocket connections by validating API keys.

```java
@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final String validApiKey;

    public WebSocketAuthInterceptor(
        @Value("${websocket.api-key}") String validApiKey) { }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        // On CONNECT frames: extract X-API-Key header or apiKey query param
        // Constant-time comparison with MessageDigest.isEqual()
        // Reject with StompHeaderAccessor error if invalid
    }
}
```

**Design Decisions:**
- Uses `ChannelInterceptor` on the inbound client channel, which intercepts STOMP frames before they reach message handlers.
- Uses `MessageDigest.isEqual()` for constant-time string comparison to prevent timing attacks.
- Fails application startup if `WEBSOCKET_API_KEY` env var is unset.

### 6. Input Validation (Jakarta Bean Validation)

**Approach:** Add Jakarta validation annotations to existing DTO classes and `@Valid` on controller method parameters.

**Modified DTOs:**
- `ApiConfigRequest`: `@NotBlank` on `name`, `url`, `method`; `@Size(max=255)` on `name`; `@Size(max=1024)` on `url`
- `WorkflowRequest`: `@NotBlank` on `name`; `@NotNull` on `workflowJson`; `@Size(max=255)` on `name`
- `ChatStartRequest`: `@NotBlank` on `sessionId`
- `ChatMessageRequest`: `@NotBlank` on `sessionId`

**Validation Error Handler** (added to `GlobalExceptionHandler`):
```java
@ExceptionHandler(MethodArgumentNotValidException.class)
public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
    List<Map<String, String>> violations = ex.getBindingResult().getFieldErrors().stream()
        .map(fe -> Map.of("field", fe.getField(), "message", fe.getDefaultMessage()))
        .toList();
    return ResponseEntity.badRequest().body(Map.of("error", "Validation failed", "violations", violations));
}
```

### 7. Profile-Based Configuration

**Files:**
- `application.properties` — Shared non-sensitive defaults (server port, JPA settings, cache TTL)
- `application-dev.properties` — Local dev database, permissive CORS, local encryption key
- `application-staging.properties` — Staging database, staging CORS origins, staging keys
- `application-prod.properties` — All sensitive values via `${ENV_VAR}` placeholders

**Design Decisions:**
- `spring.profiles.default=dev` set in base `application.properties` for safe default behavior.
- Production profile uses Spring's built-in `${VAR:}` syntax, which causes startup failure if unresolved (when no default is provided).
- Sensitive values (DB password, encryption key, API key) are NEVER in property files for prod — always environment variable references.

### 8. Docker Containerization

**Multi-Stage Dockerfile:**
- **Build stage:** `maven:3.9-eclipse-temurin-17` — runs `mvn package -DskipTests`
- **Runtime stage:** `eclipse-temurin:17-jre-alpine` — copies JAR, creates `appuser`, exposes 8080
- Non-root execution via `adduser --disabled-password --no-create-home appuser`
- Health check: `HEALTHCHECK CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1`

**Design Decision:** Uses Spring Boot Actuator health endpoint for container health checks. Requires adding `spring-boot-starter-actuator` dependency with only the health endpoint exposed.

## Data Models

### EncryptionService Internal State

| Field | Type | Description |
|-------|------|-------------|
| `secretKey` | `SecretKey` | AES-256 key decoded from Base64 environment variable |

### UrlValidator Configuration

| Field | Type | Description |
|-------|------|-------------|
| `allowedHostPatterns` | `List<String>` | Host patterns from `ssrf.allowed-hosts` property |

### ValidationResult (new record)

```java
public record ValidationResult(boolean allowed, String reason) {
    public static ValidationResult allowed() { return new ValidationResult(true, null); }
    public static ValidationResult blocked(String reason) { return new ValidationResult(false, reason); }
}
```

### Modified ApiConfig Entity

The `ApiConfig` entity gains an `@EntityListeners(ApiConfigEntityListener.class)` annotation. No schema changes — `username` and `password` columns remain `VARCHAR(255)` but now store Base64-encoded ciphertext instead of plaintext.

### Profile Configuration Properties

| Property | Dev | Staging | Prod |
|----------|-----|---------|------|
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/postgres?currentSchema=chatbot_db` | staging DB URL | `${SPRING_DATASOURCE_URL}` |
| `spring.datasource.username` | `postgres` | staging user | `${SPRING_DATASOURCE_USERNAME}` |
| `spring.datasource.password` | `postgres` | staging pass | `${SPRING_DATASOURCE_PASSWORD}` |
| `cors.allowed-origins` | `http://localhost:3000` | staging origins | `${CORS_ALLOWED_ORIGINS}` |
| `encryption.key` | dev key (base64) | staging key | `${ENCRYPTION_KEY}` |
| `websocket.api-key` | dev key | staging key | `${WEBSOCKET_API_KEY}` |
| `ssrf.allowed-hosts` | (empty — allow all non-private) | staging hosts | `${SSRF_ALLOWED_HOSTS}` |


## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system — essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Encryption round-trip preserves plaintext

*For any* valid string (including empty strings, unicode characters, and strings up to 255 characters), encrypting the string with `EncryptionService.encrypt()` and then decrypting the result with `EncryptionService.decrypt()` SHALL produce the original string.

**Validates: Requirements 1.1, 1.2, 1.6**

### Property 2: Private IP addresses are always rejected

*For any* IP address within private network ranges (10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16, 127.0.0.0/8, 169.254.0.0/16, or IPv6 loopback ::1), when a URL containing that IP is validated by `UrlValidator`, the result SHALL be `blocked`.

**Validates: Requirements 3.2**

### Property 3: Non-HTTP schemes are always rejected

*For any* URL with a scheme other than `http` or `https` (e.g., `ftp`, `file`, `gopher`, `javascript`, `data`), when validated by `UrlValidator`, the result SHALL be `blocked`.

**Validates: Requirements 3.3**

### Property 4: Allowlist enforcement blocks non-matching hosts

*For any* configured allowlist and *for any* host that does NOT match any pattern in the allowlist, when a URL with that host is validated by `UrlValidator`, the result SHALL be `blocked`.

**Validates: Requirements 3.4**

### Property 5: Whitespace-only strings are rejected by NotBlank validation

*For any* string composed entirely of whitespace characters (spaces, tabs, newlines), when submitted as a `@NotBlank`-annotated field (e.g., `name`, `url`, `method`, `sessionId`), Jakarta Bean Validation SHALL produce a constraint violation.

**Validates: Requirements 4.1, 4.2, 4.5**

### Property 6: Strings exceeding maximum length are rejected by Size validation

*For any* string whose length exceeds the configured `@Size(max=N)` constraint (255 for `name`, 1024 for `url`), Jakarta Bean Validation SHALL produce a constraint violation.

**Validates: Requirements 4.4**

## Error Handling

### Encryption Errors

| Scenario | Behavior |
|----------|----------|
| Missing `ENCRYPTION_KEY` env var | Application fails to start with `IllegalStateException` message identifying the missing variable |
| Invalid Base64 key format | Application fails to start with `IllegalArgumentException` |
| Decryption failure (wrong key or corrupted data) | `EncryptionService.decrypt()` throws `EncryptionException` (custom runtime exception) with log entry containing the ApiConfig ID |
| Null input to encrypt/decrypt | Returns null (pass-through for optional fields) |

### SSRF Validation Errors

| Scenario | Behavior |
|----------|----------|
| URL resolves to private IP | `UrlValidator` returns `ValidationResult.blocked("URL resolves to private network address")` |
| Invalid URL scheme | `ValidationResult.blocked("Only http and https schemes are allowed")` |
| Host not in allowlist | `ValidationResult.blocked("Host not in allowed hosts list")` |
| DNS resolution failure | `ValidationResult.blocked("DNS resolution failed for host: {hostname}")` |
| Blocked URL reaches HttpExecutor | `HttpExecutionResult(false, 0, null, "SSRF protection: URL blocked by security policy")` |

### Input Validation Errors

| Scenario | Behavior |
|----------|----------|
| `@NotBlank` violation | HTTP 400 with `{"error": "Validation failed", "violations": [{"field": "name", "message": "must not be blank"}]}` |
| `@Size` violation | HTTP 400 with `{"error": "Validation failed", "violations": [{"field": "name", "message": "size must be between 0 and 255"}]}` |
| `@NotNull` violation | HTTP 400 with `{"error": "Validation failed", "violations": [{"field": "workflowJson", "message": "must not be null"}]}` |
| Multiple violations | All violations returned in the `violations` array |

### WebSocket Authentication Errors

| Scenario | Behavior |
|----------|----------|
| Missing `WEBSOCKET_API_KEY` env var | Application fails to start with descriptive error |
| Missing API key in CONNECT frame | STOMP ERROR frame sent, connection closed |
| Invalid API key in CONNECT frame | STOMP ERROR frame sent, connection closed |
| Valid API key | Connection proceeds normally |

### Profile Configuration Errors

| Scenario | Behavior |
|----------|----------|
| Missing required env var in prod profile | Application fails to start with Spring's `IllegalArgumentException` identifying the unresolved placeholder |
| No profile specified | Defaults to `dev` profile |

## Testing Strategy

### Property-Based Testing (jqwik)

The project already uses **jqwik 1.8.2** for property-based testing. Each correctness property maps to a single jqwik `@Property` test with a minimum of 100 iterations (jqwik default is 1000, which exceeds the minimum).

**Test Configuration:**
- Library: jqwik 1.8.2 (already in `pom.xml`)
- Minimum iterations: 100 per property
- Each test tagged with: `// Feature: security-hardening, Property N: {property_text}`

**Property Test Classes:**
- `EncryptionServicePropertyTest` — Property 1 (round-trip)
- `UrlValidatorPropertyTest` — Properties 2, 3, 4 (SSRF validation)
- `InputValidationPropertyTest` — Properties 5, 6 (bean validation)

### Unit Tests (JUnit 5 + Mockito)

Unit tests cover specific examples, edge cases, and integration points:

- `EncryptionServiceTest` — Startup failure cases, null handling, corrupted ciphertext
- `UrlValidatorTest` — DNS rebinding scenario (mocked DNS), specific private IP examples, allowlist matching edge cases
- `WebSocketAuthInterceptorTest` — Valid/invalid/missing API key, constant-time comparison verification
- `CorsConfigTest` — Default origins, profile-specific origins
- `GlobalExceptionHandlerTest` — Validation error response format

### Integration Tests (SpringBootTest)

Integration tests verify end-to-end behavior across components:

- `CorsIntegrationTest` — HTTP requests with various Origin headers against live controllers
- `WebSocketAuthIntegrationTest` — STOMP CONNECT with valid/invalid credentials
- `SsrfProtectionIntegrationTest` — Full flow: placeholder resolution → URL validation → execution blocked
- `ProfileConfigurationTest` — Application starts correctly with each profile

### Testing Balance

- **Property tests** handle comprehensive input coverage (all valid strings for encryption, all private IPs, all whitespace variants)
- **Unit tests** handle specific examples, error paths, and mock-heavy scenarios (DNS rebinding, startup failures)
- **Integration tests** handle cross-component wiring and HTTP-level behavior (CORS headers, WebSocket auth flow)

This avoids redundant example-based tests for scenarios already covered by property-based testing while ensuring integration points are verified with targeted examples.
