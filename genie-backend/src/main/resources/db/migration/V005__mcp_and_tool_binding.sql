CREATE TABLE mcp_server (
    id VARCHAR(36) NOT NULL, tenant_id VARCHAR(36) NOT NULL, owner_id VARCHAR(36) NOT NULL,
    name VARCHAR(128) NOT NULL, server_url VARCHAR(2048) NOT NULL, auth_type VARCHAR(32) NOT NULL,
    auth_name VARCHAR(128) NULL, credential_envelope MEDIUMTEXT NULL, status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    last_check_status VARCHAR(16) NULL, last_check_code VARCHAR(64) NULL, last_checked_at DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0, created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    active_name VARCHAR(128) GENERATED ALWAYS AS (CASE WHEN deleted_at IS NULL THEN name ELSE NULL END) STORED,
    PRIMARY KEY (id), UNIQUE KEY uk_mcp_server_active_name (tenant_id, owner_id, active_name),
    KEY idx_mcp_server_owner (tenant_id, owner_id, deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE mcp_tool (
    id VARCHAR(36) NOT NULL, tenant_id VARCHAR(36) NOT NULL, owner_id VARCHAR(36) NOT NULL,
    mcp_server_id VARCHAR(36) NOT NULL, tool_name VARCHAR(256) NOT NULL, runtime_name VARCHAR(320) NOT NULL,
    description TEXT NULL, input_schema JSON NOT NULL, schema_hash CHAR(64) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE, available BOOLEAN NOT NULL DEFAULT FALSE, version BIGINT NOT NULL DEFAULT 0,
    discovered_at DATETIME(6) NULL, updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id), UNIQUE KEY uk_mcp_tool_server_name (mcp_server_id, tool_name),
    UNIQUE KEY uk_mcp_tool_runtime_name (tenant_id, owner_id, runtime_name), KEY idx_mcp_tool_server (tenant_id, owner_id, mcp_server_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE agent_tool_binding (
    tenant_id VARCHAR(36) NOT NULL, owner_id VARCHAR(36) NOT NULL, agent_id VARCHAR(36) NOT NULL,
    capability_key VARCHAR(320) NOT NULL, created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_agent_tool_binding (tenant_id, owner_id, agent_id, capability_key), KEY idx_agent_tool_binding_capability (tenant_id, owner_id, capability_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE skill_tool_binding (
    tenant_id VARCHAR(36) NOT NULL, owner_id VARCHAR(36) NOT NULL, skill_id VARCHAR(36) NOT NULL,
    capability_key VARCHAR(320) NOT NULL, created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_skill_tool_binding (tenant_id, owner_id, skill_id, capability_key), KEY idx_skill_tool_binding_capability (tenant_id, owner_id, capability_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
