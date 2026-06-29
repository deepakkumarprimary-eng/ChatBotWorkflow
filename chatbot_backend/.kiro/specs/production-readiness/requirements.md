# Requirements Document

## Introduction

The Chatbot Workflow Engine requires production-readiness enhancements to support reliable, observable, and maintainable deployment in production environments. This feature covers health monitoring, structured logging, database migration management, environment-specific configuration, stale session cleanup, graceful shutdown, and API documentation generation.

## Glossary

- **Actuator**: Spring Boot module providing operational endpoints for health checks, metrics, and application information
- **Health_Indicator**: A Spring Boot Actuator component that reports the health status of a specific subsystem
- **Micrometer**: Metrics instrumentation library integrated with Spring Boot Actuator for collecting application metrics
- **MDC**: Mapped Diagnostic Context — a thread-local map used by SLF4J/Logback to attach contextual key-value pairs to log entries
- **Correlation_ID**: A unique identifier (sessionId) propagated through all log entries related to a single workflow execution
- **Workflow_Engine**: The WorkflowExecutionServiceImpl component that processes workflow nodes and manages chat session state
- **WebSocket_Connection**: A persistent bidirectional connection between the client and server used for real-time chat execution via STOMP
- **Stale_Session**: A ChatSession entity with status "active" that has not been updated within a configurable inactivity threshold
- **Graceful_Shutdown**: The process of stopping the application while allowing in-progress workflow executions to complete safely
- **SpringDoc**: The SpringDoc OpenAPI library that generates OpenAPI 3.0 documentation from annotated Spring REST controllers
- **Environment_Profile**: A Spring Boot profile (dev, staging, prod) that activates environment-specific configuration properties

## Requirements

### Requirement 1: Spring Boot Actuator Health Monitoring

**User Story:** As an operations engineer, I want health check endpoints that report the status of critical subsystems, so that I can monitor application availability and detect failures early.

#### Acceptance Criteria

1. THE Actuator SHALL expose a health endpoint at `/actuator/health` that returns an aggregate health status
2. THE Actuator SHALL include health indicators for PostgreSQL database connectivity, disk space availability, and WebSocket_Connection pool status
3. WHEN the PostgreSQL database is unreachable, THE Health_Indicator SHALL report status as DOWN with an error description
4. WHEN active WebSocket_Connection count exceeds a configurable threshold, THE Health_Indicator SHALL report status as DEGRADED
5. THE Actuator SHALL expose a custom Health_Indicator that reports Workflow_Engine availability based on whether the service bean is active and responsive
6. THE Actuator SHALL expose a metrics endpoint at `/actuator/metrics` using Micrometer for JVM, HTTP request, and database connection pool metrics
7. THE Actuator SHALL expose an info endpoint at `/actuator/info` that returns application name, version, and build timestamp

### Requirement 2: Structured Logging with Correlation IDs

**User Story:** As a developer, I want structured JSON logs with correlation IDs, so that I can trace workflow executions across log aggregation systems and debug production issues efficiently.

#### Acceptance Criteria

1. THE Workflow_Engine SHALL use SLF4J logging throughout WorkflowExecutionServiceImpl, all NodeProcessor implementations, and PlaceholderService
2. WHEN a workflow execution begins via startWorkflow, THE Workflow_Engine SHALL place the sessionId into the MDC as a Correlation_ID
3. WHILE a Correlation_ID is present in the MDC, THE Workflow_Engine SHALL include the Correlation_ID in every log entry produced during that execution
4. WHEN a workflow execution completes or encounters a terminal error, THE Workflow_Engine SHALL remove the Correlation_ID from the MDC
5. THE Workflow_Engine SHALL log workflow start and completion events at INFO level
6. THE Workflow_Engine SHALL log individual node processing events at DEBUG level, including node type and node identifier
7. WHEN a recoverable error occurs during node processing, THE Workflow_Engine SHALL log the error at WARN level with contextual details
8. WHEN an unexpected failure occurs, THE Workflow_Engine SHALL log the full exception at ERROR level
9. THE Application SHALL output log entries in structured JSON format suitable for log aggregation tools when the prod profile is active

### Requirement 3: Environment-Specific Configuration Profiles

**User Story:** As a developer, I want environment-specific configuration profiles, so that the application uses appropriate settings for development, staging, and production deployments.

#### Acceptance Criteria

1. THE Application SHALL support three Environment_Profiles: dev, staging, and prod
2. WHILE the dev profile is active, THE Application SHALL enable SQL logging, show-sql output, and use relaxed connection pool settings
3. WHILE the staging profile is active, THE Application SHALL disable show-sql output and use moderate connection pool sizes
4. WHILE the prod profile is active, THE Application SHALL disable show-sql output, enable structured JSON logging, and use production-tuned connection pool settings with a maximum pool size of 20
5. WHILE the prod profile is active, THE Application SHALL configure HikariCP with connection timeout of 30 seconds, idle timeout of 10 minutes, and max lifetime of 30 minutes
6. IF no profile is explicitly activated, THEN THE Application SHALL default to the dev profile

### Requirement 4: Stale Session Cleanup

**User Story:** As an operations engineer, I want stale chat sessions to be automatically cleaned up, so that the database does not accumulate abandoned session records indefinitely.

#### Acceptance Criteria

1. THE Application SHALL run a scheduled cleanup job that identifies Stale_Sessions based on a configurable inactivity threshold
2. THE Application SHALL use a default inactivity threshold of 24 hours, measured from the updated_at timestamp of the ChatSession entity
3. WHEN the cleanup job executes, THE Application SHALL update the status of identified Stale_Sessions from "active" to "expired"
4. THE Application SHALL log the number of expired sessions at INFO level after each cleanup execution
5. THE Application SHALL execute the cleanup job on a configurable schedule with a default interval of 1 hour
6. WHILE the dev profile is active, THE Application SHALL allow the stale session cleanup job to be disabled via configuration

### Requirement 5: Graceful Shutdown

**User Story:** As an operations engineer, I want the application to shut down gracefully, so that in-progress workflow executions are not abruptly terminated and data consistency is preserved.

#### Acceptance Criteria

1. WHEN the application receives a shutdown signal, THE Application SHALL stop accepting new WebSocket_Connection requests
2. WHEN the application receives a shutdown signal, THE Application SHALL wait for in-progress workflow executions to reach a safe pause point before terminating
3. THE Application SHALL use a configurable shutdown timeout with a default of 30 seconds
4. IF in-progress workflow executions do not complete within the shutdown timeout, THEN THE Application SHALL force termination and log a warning with the count of interrupted executions
5. WHEN graceful shutdown begins, THE Application SHALL report the Actuator health status as OUT_OF_SERVICE
6. THE Application SHALL configure Spring Boot embedded server graceful shutdown mode

### Requirement 6: OpenAPI Documentation

**User Story:** As a developer integrating with the chatbot API, I want auto-generated API documentation, so that I can discover available endpoints, request formats, and response schemas without reading source code.

#### Acceptance Criteria

1. THE Application SHALL generate OpenAPI 3.0 documentation from annotated REST controllers using SpringDoc
2. THE Application SHALL serve the Swagger UI at `/swagger-ui.html` for interactive API exploration
3. THE Application SHALL serve the raw OpenAPI specification at `/v3/api-docs` in JSON format
4. THE Application SHALL include descriptions, request body schemas, and response schemas for all REST endpoints in WorkflowController and ApiConfigController
5. WHILE the prod profile is active, THE Application SHALL allow disabling Swagger UI access via configuration
6. THE Application SHALL group API endpoints by controller using OpenAPI tags
