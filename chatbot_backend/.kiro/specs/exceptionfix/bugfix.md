# Bugfix Requirements Document

## Introduction

The `ChatWebSocketController.handleException()` method annotated with `@MessageExceptionHandler` has an empty body. When an unhandled exception escapes the service layer and reaches the controller-level exception handler, it is silently swallowed — no error response is sent to the client, and no log entry is produced. This leaves WebSocket clients with a hung/unresponsive chat and makes debugging production issues impossible.

## Bug Analysis

### Current Behavior (Defect)

1.1 WHEN an unhandled exception bubbles up from the service layer to the `@MessageExceptionHandler` in `ChatWebSocketController` THEN the system silently swallows the exception without sending any response to the client

1.2 WHEN an unhandled exception reaches the WebSocket exception handler THEN the system produces no log entry, making it impossible to trace the failure in production

1.3 WHEN an unhandled exception occurs during WebSocket message processing and the service layer's own error handling misses it THEN the client's chat session appears hung with no indication that an error occurred

### Expected Behavior (Correct)

2.1 WHEN an unhandled exception bubbles up from the service layer to the `@MessageExceptionHandler` in `ChatWebSocketController` THEN the system SHALL send a `ChatErrorResponse` to the client on `/topic/chat/{sessionId}` with a generic error message

2.2 WHEN an unhandled exception reaches the WebSocket exception handler THEN the system SHALL log the exception at ERROR level with the correlation ID (session ID), the STOMP session ID, and the exception details

2.3 WHEN an unhandled exception occurs and the session ID can be resolved from the message headers THEN the system SHALL use the existing `BufferedMessageSender.sendError()` mechanism to deliver the error response to the correct client session

### Unchanged Behavior (Regression Prevention)

3.1 WHEN exceptions are caught and handled within the service layer (e.g., `WorkflowExecutionServiceImpl` catch blocks) THEN the system SHALL CONTINUE TO handle those errors at the service layer without triggering the controller-level exception handler

3.2 WHEN a normal (non-exceptional) WebSocket message is processed successfully THEN the system SHALL CONTINUE TO deliver the response without any error response being sent

3.3 WHEN the session ID cannot be resolved from message headers (null session context) THEN the system SHALL CONTINUE TO log the error without crashing, and SHALL NOT attempt to send a response to a null destination
