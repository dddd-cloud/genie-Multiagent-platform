CREATE TABLE conversation_attachment (
    id               VARCHAR(36)   NOT NULL,
    conversation_id  VARCHAR(36)   NOT NULL,
    tenant_id        VARCHAR(36)   NOT NULL,
    owner_id         VARCHAR(36)   NOT NULL,
    file_name        VARCHAR(255)  NOT NULL,
    file_type        VARCHAR(16)   NOT NULL,
    mime_type        VARCHAR(127)  NOT NULL,
    size_bytes       BIGINT        NOT NULL,
    extracted_text   MEDIUMTEXT    NOT NULL,
    truncated        TINYINT       NOT NULL DEFAULT 0,
    created_at       DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_attach_conv (conversation_id, created_at),
    CONSTRAINT fk_attach_conversation FOREIGN KEY (conversation_id) REFERENCES conversation(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
