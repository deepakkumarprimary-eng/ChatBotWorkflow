# Bugfix Requirements Document

## Introduction

This spec addresses three production-reliability bugs that affect the chatbot backend under real-world deployment conditions:

1. **Data Race on Concurrent Messages** — The `ChatSession` entity lacks optimistic locking (`@Version`). Two simultaneous `chat.message` calls for the same session can overwrite each other's context data, causing lost user inputs and corrupted navigation history.

2. **Missing Reverse Proxy Configuration** — The application does not set `server.forward-headers-strategy=native`. When deployed behind a load balancer (K8s Ingress, Nginx), the app incorrectly resolves client IPs and cannot detect HTTPS, breaking audit logging and secure-cookie behavior.

3. **Cache Bypass in ChildWorkflowService and NavigationService** — Both `ChildWorkflowService` and `NavigationService` inject `WorkflowRepository` directly and call `workflowRepository.findById()` instead of using `WorkflowCacheService`. This hits PostgreSQL on every child workflow entry/exit and every back/restart navigation, negating the Redis caching layer.

## Bug Analysis

### Current Behavior (Defect)

1.1 WHEN two concurrent `chat.message` WebSocket frames arrive for the same session THEN the system allows both transactions to read the same `ChatSession` row, apply independent context mutations, and the last writer silently overwrites the first writer's changes (lost update)

1.2 WHEN the application is deployed behind a reverse proxy (Nginx, K8s Ingress, ALB) THEN the system resolves `request.getRemoteAddr()` as the proxy's IP address instead of the real client IP

1.3 WHEN the application is deployed behind a TLS-terminating reverse proxy THEN the system reports `request.isSecure()` as `false` even though the original client connection was HTTPS

1.4 WHEN `ChildWorkflowService.enterChild()` is called THEN the system queries PostgreSQL directly via `workflowRepository.findById(childWorkflowId)` bypassing the Redis cache

1.5 WHEN `ChildWorkflowService.handleChildEnd()` is called THEN the system queries PostgreSQL directly via `workflowRepository.findById(parentWorkflowId)` bypassing the Redis cache

1.6 WHEN `NavigationService.handleBack()` is called THEN the system queries PostgreSQL directly via `workflowRepository.findById(targetWorkflowId)` bypassing the Redis cache

1.7 WHEN `NavigationService.handleRestart()` is called THEN the system queries PostgreSQL directly via `workflowRepository.findById(rootWorkflowId)` bypassing the Redis cache

### Expected Behavior (Correct)

2.1 WHEN two concurrent `chat.message` WebSocket frames arrive for the same session THEN the system SHALL detect the conflict via an optimistic locking version check and reject the stale write with an `ObjectOptimisticLockingFailureException` (or equivalent), preventing lost updates

2.2 WHEN the application is deployed behind a reverse proxy THEN the system SHALL resolve client IPs from `X-Forwarded-For` / `X-Real-IP` headers by configuring `server.forward-headers-strategy=native`

2.3 WHEN the application is deployed behind a TLS-terminating reverse proxy THEN the system SHALL detect the original HTTPS protocol from `X-Forwarded-Proto` header

2.4 WHEN `ChildWorkflowService.enterChild()` is called THEN the system SHALL retrieve the workflow via `WorkflowCacheService.findById()` to leverage Redis caching

2.5 WHEN `ChildWorkflowService.handleChildEnd()` is called THEN the system SHALL retrieve the workflow via `WorkflowCacheService.findById()` to leverage Redis caching

2.6 WHEN `NavigationService.handleBack()` is called THEN the system SHALL retrieve the workflow via `WorkflowCacheService.findById()` to leverage Redis caching

2.7 WHEN `NavigationService.handleRestart()` is called THEN the system SHALL retrieve the workflow via `WorkflowCacheService.findById()` to leverage Redis caching

### Unchanged Behavior (Regression Prevention)

3.1 WHEN a single `chat.message` arrives with no concurrent writes THEN the system SHALL CONTINUE TO save context updates successfully without version conflicts

3.2 WHEN the application runs locally without a reverse proxy (dev profile) THEN the system SHALL CONTINUE TO resolve the direct client IP correctly

3.3 WHEN `WorkflowExecutionServiceImpl` calls `WorkflowCacheService.findById()` for workflow loading THEN the system SHALL CONTINUE TO use the cache layer as before

3.4 WHEN a workflow is updated or deleted via the REST API THEN the system SHALL CONTINUE TO evict the cached entry so subsequent reads reflect the change

3.5 WHEN Redis is unavailable THEN `WorkflowCacheService` SHALL CONTINUE TO fall back to PostgreSQL transparently without errors propagating to the caller
