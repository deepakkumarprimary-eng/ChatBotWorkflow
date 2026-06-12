-- Chatbot Workflow Builder - Initial Database Schema
-- Flyway Migration V1
-- Requirements: 3.1, 3.5, 5.1, 8.1, 8.2, 9.5

-- Ensure the chatbot schema exists
CREATE SCHEMA IF NOT EXISTS chatbot;

-- Set search path to chatbot schema
SET search_path TO chatbot;

-- Workflows table
CREATE TABLE workflows (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    description TEXT,
    current_version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_modified_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP WITH TIME ZONE -- soft delete
);

CREATE INDEX idx_workflows_last_modified ON workflows(last_modified_at DESC)
    WHERE deleted_at IS NULL;

-- Workflow versions table (stores each version's definition)
CREATE TABLE workflow_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id UUID NOT NULL REFERENCES workflows(id),
    version INTEGER NOT NULL,
    definition JSONB NOT NULL, -- Full WorkflowDefinition JSON
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE(workflow_id, version)
);

CREATE INDEX idx_workflow_versions_workflow ON workflow_versions(workflow_id, version DESC);

-- Executions table
CREATE TABLE executions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id UUID NOT NULL REFERENCES workflows(id),
    workflow_version INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'running'
        CHECK (status IN ('running', 'completed', 'failed', 'paused')),
    current_state_id UUID,
    context_variables JSONB NOT NULL DEFAULT '{}',
    start_time TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    end_time TIMESTAMP WITH TIME ZONE,
    max_duration_seconds INTEGER NOT NULL DEFAULT 3600,
    error_message TEXT,
    error_stack_trace VARCHAR(5000)
);

CREATE INDEX idx_executions_workflow ON executions(workflow_id);
CREATE INDEX idx_executions_status ON executions(status) WHERE status = 'running';
CREATE INDEX idx_executions_start_time ON executions(start_time DESC);

-- Execution history (state transitions)
CREATE TABLE execution_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    execution_id UUID NOT NULL REFERENCES executions(id),
    state_id UUID NOT NULL,
    state_name VARCHAR(255),
    entry_time TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    exit_time TIMESTAMP WITH TIME ZONE,
    outcome VARCHAR(20)
        CHECK (outcome IN ('succeeded', 'failed', 'skipped', 'timed_out')),
    context_snapshot JSONB, -- Context variables at this transition
    error_message TEXT,
    error_stack_trace VARCHAR(5000),
    sequence_number INTEGER NOT NULL -- ordering within execution
);

CREATE INDEX idx_execution_history_execution ON execution_history(execution_id, sequence_number);

-- Retry attempts
CREATE TABLE retry_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    execution_id UUID NOT NULL REFERENCES executions(id),
    state_id UUID NOT NULL,
    attempt_number INTEGER NOT NULL,
    attempted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    error_message TEXT
);

CREATE INDEX idx_retry_attempts_execution ON retry_attempts(execution_id, state_id);
