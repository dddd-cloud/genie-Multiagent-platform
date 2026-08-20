CREATE TABLE user_llm_model (
    id               VARCHAR(36)   NOT NULL,
    tenant_id        VARCHAR(36)   NOT NULL,
    owner_id         VARCHAR(36)   NOT NULL,
    name             VARCHAR(128)  NOT NULL,
    display_name     VARCHAR(128)  NOT NULL,
    model            VARCHAR(256)  NOT NULL,
    base_url         VARCHAR(2048) NOT NULL DEFAULT '',
    interface_url    VARCHAR(512)  NOT NULL DEFAULT '/v1/chat/completions',
    max_tokens       INT           NOT NULL DEFAULT 16384,
    temperature      DOUBLE        NOT NULL DEFAULT 0,
    max_input_tokens INT           NOT NULL DEFAULT 100000,
    api_key_envelope MEDIUMTEXT    NULL,
    created_at       DATETIME(6)   NOT NULL,
    updated_at       DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_llm_model_name (tenant_id, owner_id, name),
    KEY idx_user_llm_model_owner (tenant_id, owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
