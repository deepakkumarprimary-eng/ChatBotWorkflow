# Requirements Document

## Introduction

This feature addresses critical security vulnerabilities in the Chatbot Workflow Engine backend. The application currently exposes sensitive credentials in plaintext, lacks input validation, has no SSRF protections, uses permissive CORS settings scattered across controllers, and allows unauthenticated WebSocket connections. This spec defines hardening measures across six areas: credential externalization, encryption at rest, CORS centralization, SSRF protection, input validation, and WebSocket authentication.

## Glossary

- **Application**: The Spring Boot Chatbot Workflow Engine backend service
- **Credential_Manager**: The subsystem responsible for loading and providing database credentials from external sources (environment variables or Spring profile-specific property files)
- **Encryption_Service**: The service component responsible for encrypting and decrypting sensitive fields using AES-256 symmetric encryption
- **CORS_Configuration**: The centralized Spring configuration bean that defines allowed origins, methods, and headers for cross-origin requests
- **URL_Validator**: The component that validates resolved URLs against an allowlist before HTTP requests are executed
- **Input_Validator**: The validation layer using Jakarta Bean Validation annotations on request DTOs
- **WebSocket_Authenticator**: The component that verifies API key or token credentials on WebSocket connection attempts
- **ApiConfig**: A JPA entity representing a reusable API call configuration stored in the `api_config` database table
- **PlaceholderService**: The service that resolves `{{variable}}` placeholders in URL templates using session context values
- **HttpExecutor**: The component that executes outbound HTTP requests on behalf of API workflow nodes
- **Encryption_Key**: An AES-256 symmetric key stored externally (environment variable or secrets manager), used by the Encryption_Service
- **Allowed_Hosts**: A configurable list of permitted URL host patterns that the URL_Validator uses to approve outbound requests

## Requirements

### Requirement 1: Externalize Database Credentials

**User Story:** As a DevOps engineer, I want database credentials removed from source-controlled property files and loaded from environment variables or profile-specific configurations, so that secrets are not exposed in the repository and differ across deployment environments.

#### Acceptance Criteria

1. THE Application SHALL load database connection URL, username, and password from environment variables (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`) when no Spring profile is active
2. WHERE a Spring profile is active (dev, staging, or prod), THE Credential_Manager SHALL load database credentials from the corresponding profile-specific property file (`application-{profile}.properties`)
3. WHEN the required database environment variables are missing and no profile is active, THE Application SHALL fail to start with a descriptive error message indicating which variables are missing
4. THE Application SHALL NOT contain plaintext database credentials in `application.properties` or any source-controlled file

### Requirement 2: Encrypt ApiConfig Credentials at Rest

**User Story:** As a security engineer, I want API configuration credentials (username and password) encrypted before storage in the database and decrypted only when read, so that a database breach does not expose plaintext credentials.

#### Acceptance Criteria

1. WHEN an ApiConfig entity is persisted, THE Encryption_Service SHALL encrypt the `username` and `password` fields using AES-256 encryption before writing to the database
2. WHEN an ApiConfig entity is read from the database, THE Encryption_Service SHALL decrypt the `username` and `password` fields before returning them to the service layer
3. THE Encryption_Service SHALL load the Encryption_Key from an environment variable (`ENCRYPTION_KEY`) at application startup
4. WHEN the `ENCRYPTION_KEY` environment variable is missing, THE Application SHALL fail to start with a descriptive error message
5. IF the Encryption_Service encounters a decryption failure (corrupted data or wrong key), THEN THE Encryption_Service SHALL throw a runtime exception with a log entry identifying the affected ApiConfig record
6. FOR ALL valid username and password strings, encrypting then decrypting SHALL produce the original plaintext value (round-trip property)

### Requirement 3: Centralize CORS Configuration

**User Story:** As a developer, I want a single centralized CORS configuration with environment-specific allowed origins, so that CORS policy is consistent across all endpoints and not permissive by default.

#### Acceptance Criteria

1. THE CORS_Configuration SHALL define allowed origins, HTTP methods, and headers in a single Spring configuration bean
2. THE CORS_Configuration SHALL apply to all REST controller endpoints and WebSocket endpoints
3. WHERE a Spring profile is active, THE CORS_Configuration SHALL load allowed origins from profile-specific configuration (`cors.allowed-origins` property)
4. WHEN no profile-specific allowed origins are configured, THE CORS_Configuration SHALL default to `http://localhost:3000` (development frontend)
5. THE Application SHALL NOT contain `@CrossOrigin` annotations on individual controller classes or methods
6. WHEN a request origin does not match the configured allowed origins, THE CORS_Configuration SHALL reject the request with an appropriate HTTP 403 response

### Requirement 4: SSRF Protection in PlaceholderService

**User Story:** As a security engineer, I want outbound HTTP requests validated against an allowlist after placeholder resolution, so that malicious context variable values cannot cause the server to access internal or private network endpoints.

#### Acceptance Criteria

1. WHEN the PlaceholderService resolves a URL template, THE URL_Validator SHALL validate the resolved URL before the HttpExecutor executes the request
2. THE URL_Validator SHALL reject URLs that resolve to private network IP ranges (10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16, 127.0.0.0/8, 169.254.0.0/16, and IPv6 loopback)
3. THE URL_Validator SHALL reject URLs using schemes other than `http` or `https`
4. WHERE the `ssrf.allowed-hosts` property is configured, THE URL_Validator SHALL only permit requests to hosts matching the Allowed_Hosts patterns
5. IF the URL_Validator rejects a resolved URL, THEN THE HttpExecutor SHALL NOT execute the request and SHALL return an error result indicating the URL was blocked
6. WHEN DNS resolution of a URL hostname resolves to a private IP address, THE URL_Validator SHALL reject the URL to prevent DNS rebinding attacks

### Requirement 5: Input Validation on Request DTOs

**User Story:** As a developer, I want all REST request bodies validated using Jakarta Bean Validation annotations, so that malformed or missing data is rejected at the controller layer with clear error messages.

#### Acceptance Criteria

1. THE Input_Validator SHALL enforce `@NotBlank` on required string fields in ApiConfigRequest (`name`, `url`, `method`)
2. THE Input_Validator SHALL enforce `@NotBlank` on required string fields in WorkflowRequest (`name`)
3. THE Input_Validator SHALL enforce `@NotNull` on required object fields in WorkflowRequest (`workflowJson`)
4. THE Input_Validator SHALL enforce `@Size` constraints on string fields: `name` maximum 255 characters, `url` maximum 1024 characters
5. THE Input_Validator SHALL enforce `@NotBlank` on `sessionId` in ChatStartRequest and ChatMessageRequest DTOs
6. WHEN validation fails on a REST request, THE Application SHALL return HTTP 400 with a response body listing each field violation (field name and violation message)
7. THE Application SHALL apply `@Valid` annotation on all `@RequestBody` parameters in REST controller methods

### Requirement 6: WebSocket Session Authentication

**User Story:** As a security engineer, I want WebSocket connections authenticated via an API key, so that only authorized clients can initiate or participate in chatbot sessions.

#### Acceptance Criteria

1. WHEN a client attempts a WebSocket STOMP connection, THE WebSocket_Authenticator SHALL require a valid API key in the connection headers (`X-API-Key` header or `apiKey` query parameter)
2. IF a WebSocket connection attempt lacks a valid API key, THEN THE WebSocket_Authenticator SHALL reject the connection with a STOMP ERROR frame and close the connection
3. THE WebSocket_Authenticator SHALL load the valid API key value from an environment variable (`WEBSOCKET_API_KEY`)
4. WHEN the `WEBSOCKET_API_KEY` environment variable is missing, THE Application SHALL fail to start with a descriptive error message
5. WHEN a valid API key is provided, THE WebSocket_Authenticator SHALL allow the STOMP connection to proceed and associate it with the authenticated session
6. THE WebSocket_Authenticator SHALL use constant-time string comparison when validating API keys to prevent timing attacks
