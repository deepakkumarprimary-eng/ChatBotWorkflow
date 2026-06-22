CREATE TABLE IF NOT EXISTS workflow (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    workflow_json JSONB,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS chat_session (
    id                BIGSERIAL PRIMARY KEY,
    session_id        VARCHAR(255) NOT NULL UNIQUE,
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

-- API Node Configuration tables
CREATE TABLE IF NOT EXISTS api_config (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL UNIQUE,
    url         VARCHAR(1024) NOT NULL,
    method      VARCHAR(10) NOT NULL CHECK (method IN ('GET','POST','PUT','DELETE')),
    timeout_ms  INTEGER NOT NULL DEFAULT 5000 CHECK (timeout_ms >= 1 AND timeout_ms <= 300000),
    retry_count INTEGER NOT NULL DEFAULT 1 CHECK (retry_count >= 0 AND retry_count <= 10),
    username    VARCHAR(255),
    password    VARCHAR(255),
    client_id   VARCHAR(255),
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS api_header (
    id           BIGSERIAL PRIMARY KEY,
    api_id       BIGINT NOT NULL REFERENCES api_config(id) ON DELETE CASCADE,
    header_name  VARCHAR(255) NOT NULL,
    header_value VARCHAR(1024) NOT NULL
);

CREATE TABLE IF NOT EXISTS api_payload (
    id               BIGSERIAL PRIMARY KEY,
    api_id           BIGINT NOT NULL UNIQUE REFERENCES api_config(id) ON DELETE CASCADE,
    payload_template JSONB NOT NULL,
    created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS api_response_mapping (
    id                    BIGSERIAL PRIMARY KEY,
    api_id                BIGINT NOT NULL REFERENCES api_config(id) ON DELETE CASCADE,
    response_path         VARCHAR(512) NOT NULL,
    context_variable_name VARCHAR(255) NOT NULL
        CHECK (context_variable_name ~ '^[a-zA-Z_][a-zA-Z0-9_]*$'),
    created_at            TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (api_id, context_variable_name)
);

-- Note: context_variable_name column is defined in the CREATE TABLE above.
-- The guarded DO $$ migration block was removed because Spring Boot's SQL
-- initializer does not support dollar-quoting. The column already exists.

CREATE INDEX IF NOT EXISTS idx_api_header_api_id ON api_header(api_id);
CREATE INDEX IF NOT EXISTS idx_api_payload_api_id ON api_payload(api_id);
CREATE INDEX IF NOT EXISTS idx_api_response_mapping_api_id ON api_response_mapping(api_id);
