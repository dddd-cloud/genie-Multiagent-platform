CREATE TABLE model_usage_record (
    id                   VARCHAR(36)  NOT NULL,
    tenant_id            VARCHAR(36)  NOT NULL,
    user_id              VARCHAR(36)  NOT NULL,
    conversation_id      VARCHAR(36)  NULL,
    request_id           VARCHAR(64)  NULL,
    assistant_message_id VARCHAR(36)  NOT NULL,
    model_name           VARCHAR(128) NULL,
    prompt_tokens        BIGINT       NOT NULL DEFAULT 0,
    completion_tokens    BIGINT       NOT NULL DEFAULT 0,
    total_tokens         BIGINT       NOT NULL DEFAULT 0,
    duration_ms          BIGINT       NULL,
    terminal_state       VARCHAR(16)  NOT NULL,
    created_at           DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    -- One terminal state per assistant turn: a retried/duplicated terminal event must not double count.
    UNIQUE KEY uk_usage_message (tenant_id, assistant_message_id),
    KEY idx_usage_owner_time (tenant_id, user_id, created_at),
    KEY idx_usage_tenant_time (tenant_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
