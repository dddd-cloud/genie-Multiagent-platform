CREATE TABLE agent_team (
    id              VARCHAR(36)   NOT NULL,
    tenant_id       VARCHAR(36)   NOT NULL,
    owner_id        VARCHAR(36)   NOT NULL,
    name            VARCHAR(128)  NOT NULL,
    description     VARCHAR(1000) NOT NULL,
    master_agent_id VARCHAR(36)   NOT NULL,
    version         BIGINT        NOT NULL DEFAULT 0,
    created_at      DATETIME(6)   NOT NULL,
    updated_at      DATETIME(6)   NOT NULL,
    deleted_at      DATETIME(6)   NULL,
    active_name     VARCHAR(128)  GENERATED ALWAYS AS (
        CASE WHEN deleted_at IS NULL THEN name ELSE NULL END
    ) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_team_active_name (tenant_id, owner_id, active_name),
    KEY idx_agent_team_owner (tenant_id, owner_id, deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE agent_team_member (
    tenant_id  VARCHAR(36) NOT NULL,
    owner_id   VARCHAR(36) NOT NULL,
    team_id    VARCHAR(36) NOT NULL,
    agent_id   VARCHAR(36) NOT NULL,
    sort_order INT         NOT NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_agent_team_member (team_id, agent_id),
    UNIQUE KEY uk_agent_team_member_sort (team_id, sort_order),
    KEY idx_agent_team_member_agent (tenant_id, owner_id, agent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
