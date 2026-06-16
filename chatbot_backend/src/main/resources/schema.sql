CREATE TABLE IF NOT EXISTS workflow (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    workflow_json JSONB,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS chat_session (
    id                BIGSERIAL PRIMARY KEY,
    session_id        UUID NOT NULL UNIQUE,
    workflow_id       BIGINT NOT NULL REFERENCES workflow(id),
    current_node_id   VARCHAR(255),
    current_type      VARCHAR(50),
    current_node_type VARCHAR(50),
    context           JSONB DEFAULT '{}',
    status            VARCHAR(20) NOT NULL DEFAULT 'active',
    created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_chat_session_session_id ON chat_session(session_id);
CREATE INDEX IF NOT EXISTS idx_chat_session_status ON chat_session(status);
