CREATE TABLE user_setting (
    id            VARCHAR(36)   NOT NULL,
    tenant_id     VARCHAR(36)   NOT NULL,
    user_id       VARCHAR(36)   NOT NULL,
    setting_key   VARCHAR(64)   NOT NULL,
    setting_value VARCHAR(4096) NOT NULL,
    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_setting_owner_key (tenant_id, user_id, setting_key),
    KEY idx_setting_owner (tenant_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
