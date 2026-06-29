# Requirements Document

## Introduction

This feature addresses critical security vulnerabilities in the Chatbot Workflow Engine backend. The application currently lacks input validation, has no SSRF protections, uses permissive CORS settings scattered across controllers, and allows unauthenticated WebSocket connections. This spec defines hardening measures across seven areas: encryption at rest, CORS centralization, SSRF protection, input validation, WebSocket authentication, profile-based configuration, and Docker containerization.

## Glossary

- **Application**: The Spring Boot Chatbot Workflow Engine backend service
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
- **Profile_Configuration**: The set of Spring profile-specific property files (application-dev.properties, application-staging.properties, application-prod.properties) that supply environment-specific settings to the Application
- **Active_Profile**: The Spring profile activated at runtime via the `spring.profiles.active` property (set as an environment variable or JVM argument)
- **Dockerfile**: A multi-stage build definition file that specifies how to compile and package the Application into a Container_Image
- **Container_Image**: The Docker image artifact produced by building the Dockerfile, containing the Application JAR and a minimal JRE runtime
- **Docker_Build_Stage**: The first stage of the multi-stage Dockerfile that uses Maven to compile and package the Application into an executable JAR
- **Docker_Runtime_Stage**: The second stage of the multi-stage Dockerfile that copies the packaged JAR into a minimal JRE base image for execution
- **Dockerignore_File**: A `.dockerignore` file that excludes unnecessary files and directories from the Docker build context

## Requirements

### Requirement 1: Encrypt ApiConfig Credentials at Rest

**User Story:** As a security engineer, I want API configuration credentials (username and password) encrypted before storage in the database and decrypted only when read, so that a database breach does not expose plaintext credentials.

#### Acceptance Criteria

1. WHEN an ApiConfig entity is persisted, THE Encryption_Service SHALL encrypt the `username` and `password` fields using AES-256 encryption before writing to the database
2. WHEN an ApiConfig entity is read from the database, THE Encryption_Service SHALL decrypt the `username` and `password` fields before returning them to the service layer
3. THE Encryption_Service SHALL load the Encryption_Key from an environment variable (`ENCRYPTION_KEY`) at application startup
4. WHEN the `ENCRYPTION_KEY` environment variable is missing, THE Application SHALL fail to start with a descriptive error message
5. IF the Encryption_Service encounters a decryption failure (corrupted data or wrong key), THEN THE Encryption_Service SHALL throw a runtime exception with a log entry identifying the affected ApiConfig record
6. FOR ALL valid username and password strings, encrypting then decrypting SHALL produce the original plaintext value (round-trip property)

### Requirement 2: Centralize CORS Configuration

**User Story:** As a developer, I want a single centralized CORS configuration with environment-specific allowed origins, so that CORS policy is consistent across all endpoints and not permissive by default.

#### Acceptance Criteria

1. THE CORS_Configuration SHALL define allowed origins, HTTP methods, and headers in a single Spring configuration bean
2. THE CORS_Configuration SHALL apply to all REST controller endpoints and WebSocket endpoints
3. WHERE a Spring profile is active, THE CORS_Configuration SHALL load allowed origins from profile-specific configuration (`cors.allowed-origins` property)
4. WHEN no profile-specific allowed origins are configured, THE CORS_Configuration SHALL default to `http://localhost:3000` (development frontend)
5. THE Application SHALL NOT contain `@CrossOrigin` annotations on individual controller classes or methods
6. WHEN a request origin does not match the configured allowed origins, THE CORS_Configuration SHALL reject the request with an appropriate HTTP 403 response
7. WHEN a request does not include an Origin header, THE CORS_Configuration SHALL reject the request with an appropriate HTTP 403 response

### Requirement 3: SSRF Protection in PlaceholderService

**User Story:** As a security engineer, I want outbound HTTP requests validated against an allowlist after placeholder resolution, so that malicious context variable values cannot cause the server to access internal or private network endpoints.

#### Acceptance Criteria

1. WHEN the PlaceholderService resolves a URL template, THE URL_Validator SHALL validate the resolved URL before the HttpExecutor executes the request
2. THE URL_Validator SHALL reject URLs that resolve to private network IP ranges (10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16, 127.0.0.0/8, 169.254.0.0/16, and IPv6 loopback)
3. THE URL_Validator SHALL reject URLs using schemes other than `http` or `https`
4. WHERE the `ssrf.allowed-hosts` property is configured, THE URL_Validator SHALL only permit requests to hosts matching the Allowed_Hosts patterns. WHEN the `ssrf.allowed-hosts` property is NOT configured, THE URL_Validator SHALL permit requests to any host that is not in a private network IP range
5. IF the URL_Validator rejects a resolved URL, THEN THE HttpExecutor SHALL NOT execute the request and SHALL return an error result indicating the URL was blocked
6. WHEN DNS resolution of a URL hostname resolves to a private IP address, THE URL_Validator SHALL reject the URL to prevent DNS rebinding attacks
7. THE HttpExecutor SHALL perform its own URL validation check before executing any request, regardless of upstream validation, as a defensive measure

### Requirement 4: Input Validation on Request DTOs

**User Story:** As a developer, I want all REST request bodies validated using Jakarta Bean Validation annotations, so that malformed or missing data is rejected at the controller layer with clear error messages.

#### Acceptance Criteria

1. THE Input_Validator SHALL enforce `@NotBlank` on required string fields in ApiConfigRequest (`name`, `url`, `method`)
2. THE Input_Validator SHALL enforce `@NotBlank` on required string fields in WorkflowRequest (`name`)
3. THE Input_Validator SHALL enforce `@NotNull` on required object fields in WorkflowRequest (`workflowJson`)
4. THE Input_Validator SHALL enforce `@Size` constraints on string fields: `name` maximum 255 characters, `url` maximum 1024 characters
5. THE Input_Validator SHALL enforce `@NotBlank` on `sessionId` in ChatStartRequest and ChatMessageRequest DTOs
6. WHEN validation fails on a REST request, THE Application SHALL return HTTP 400 with a response body listing each field violation (field name and violation message)
7. THE Application SHALL apply `@Valid` annotation on all `@RequestBody` parameters in REST controller methods

### Requirement 5: WebSocket Session Authentication

**User Story:** As a security engineer, I want WebSocket connections authenticated via an API key, so that only authorized clients can initiate or participate in chatbot sessions.

#### Acceptance Criteria

1. WHEN a client attempts a WebSocket STOMP connection, THE WebSocket_Authenticator SHALL require a valid API key in the connection headers (`X-API-Key` header or `apiKey` query parameter)
2. IF a WebSocket connection attempt lacks a valid API key, THEN THE WebSocket_Authenticator SHALL reject the connection with a STOMP ERROR frame and close the connection
3. THE WebSocket_Authenticator SHALL load the valid API key value from an environment variable (`WEBSOCKET_API_KEY`)
4. WHEN the `WEBSOCKET_API_KEY` environment variable is missing, THE Application SHALL fail to start with a descriptive error message
5. WHEN a valid API key is provided, THE WebSocket_Authenticator SHALL allow the STOMP connection to proceed and associate it with the authenticated session
6. THE WebSocket_Authenticator SHALL use constant-time string comparison when validating API keys to prevent timing attacks

### Requirement 6: Spring Profile-Based Configuration

**User Story:** As a DevOps engineer, I want environment-specific configuration files for dev, staging, and production profiles, so that each environment uses appropriate database credentials, CORS origins, encryption keys, and API keys without manual reconfiguration at deployment time.

#### Acceptance Criteria

1. THE Application SHALL provide three profile-specific property files: `application-dev.properties`, `application-staging.properties`, and `application-prod.properties`
2. THE Application SHALL activate the correct Profile_Configuration based on the `spring.profiles.active` value supplied as an environment variable or JVM argument
3. WHEN no Active_Profile is specified, THE Application SHALL default to the `dev` profile
4. THE base `application.properties` SHALL contain only shared, non-sensitive default settings that apply across all environments
5. WHEN the `dev` profile is active, THE Profile_Configuration SHALL provide development-specific database credentials, permissive CORS origins (`http://localhost:3000`), and local encryption key values
6. WHEN the `staging` profile is active, THE Profile_Configuration SHALL provide staging-specific database credentials, staging CORS origins, and staging encryption key values
7. WHEN the `prod` profile is active, THE Profile_Configuration SHALL reference sensitive values (database credentials, encryption keys, API keys) using environment variable placeholders (`${ENV_VAR_NAME}`) rather than hardcoded values
8. IF a required environment variable placeholder referenced in the `prod` Profile_Configuration is not set at runtime, THEN THE Application SHALL fail to start with a descriptive error identifying the missing variable
9. THE Profile_Configuration files SHALL each define values for: database connection URL, database username, database password, CORS allowed origins, encryption key, and WebSocket API key

### Requirement 7: Docker Containerization

**User Story:** As a DevOps engineer, I want the application containerized using a multi-stage Dockerfile with security best practices, so that deployment is reproducible, portable, and follows the principle of least privilege.

#### Acceptance Criteria

1. THE Dockerfile SHALL use a multi-stage build with a Docker_Build_Stage and a Docker_Runtime_Stage
2. THE Docker_Build_Stage SHALL use a Maven base image to compile and package the Application into an executable JAR using `mvn package -DskipTests`
3. THE Docker_Runtime_Stage SHALL use `eclipse-temurin:17-jre-alpine` as the base image to minimize the Container_Image size and attack surface
4. THE Docker_Runtime_Stage SHALL copy only the packaged JAR from the Docker_Build_Stage into the final Container_Image
5. THE Docker_Runtime_Stage SHALL create and switch to a non-root user (`appuser`) for running the Application process
6. THE Container_Image SHALL expose port 8080 for inbound HTTP and WebSocket traffic
7. THE Dockerfile SHALL accept environment variables for all sensitive configuration: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `ENCRYPTION_KEY`, `WEBSOCKET_API_KEY`, and `SPRING_PROFILES_ACTIVE`
8. WHEN the Container_Image is built, THE Dockerfile SHALL produce a minimal number of layers by combining related RUN commands
9. THE Dockerignore_File SHALL exclude `.git`, `target/`, `.idea/`, `*.iml`, `.kiro/`, `.vscode/`, `.cursor/`, `*.log`, and `docs/` from the Docker build context
10. THE Docker_Runtime_Stage SHALL define a health check or expose a mechanism for container orchestrators to verify Application readiness
