CREATE TABLE agent_definition (
    id             VARCHAR(36)   NOT NULL,
    tenant_id      VARCHAR(36)   NOT NULL,
    owner_id       VARCHAR(36)   NOT NULL,
    name           VARCHAR(128)  NOT NULL,
    description    VARCHAR(1000) NOT NULL,
    prompt_mode    VARCHAR(16)   NOT NULL,
    prompt_config  JSON          NULL,
    system_prompt  MEDIUMTEXT    NOT NULL,
    model_name     VARCHAR(128)  NULL,
    status         VARCHAR(16)   NOT NULL,
    version        BIGINT        NOT NULL DEFAULT 0,
    created_at     DATETIME(6)   NOT NULL,
    updated_at     DATETIME(6)   NOT NULL,
    deleted_at     DATETIME(6)   NULL,
    active_name    VARCHAR(128)  GENERATED ALWAYS AS (
        CASE WHEN deleted_at IS NULL THEN name ELSE NULL END
    ) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_active_name (tenant_id, owner_id, active_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE skill_definition (
    id                  VARCHAR(36)   NOT NULL,
    tenant_id           VARCHAR(36)   NOT NULL,
    owner_id            VARCHAR(36)   NOT NULL,
    name                VARCHAR(128)  NOT NULL,
    description         VARCHAR(1000) NOT NULL,
    instruction         MEDIUMTEXT    NOT NULL,
    output_requirement  TEXT          NULL,
    status              VARCHAR(16)   NOT NULL,
    version             BIGINT        NOT NULL DEFAULT 0,
    created_at          DATETIME(6)   NOT NULL,
    updated_at          DATETIME(6)   NOT NULL,
    deleted_at          DATETIME(6)   NULL,
    active_name         VARCHAR(128)  GENERATED ALWAYS AS (
        CASE WHEN deleted_at IS NULL THEN name ELSE NULL END
    ) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_skill_active_name (tenant_id, owner_id, active_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE agent_skill_binding (
    tenant_id   VARCHAR(36) NOT NULL,
    owner_id    VARCHAR(36) NOT NULL,
    agent_id    VARCHAR(36) NOT NULL,
    skill_id    VARCHAR(36) NOT NULL,
    sort_order  INT         NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    UNIQUE KEY uk_agent_skill_agent_skill (agent_id, skill_id),
    UNIQUE KEY uk_agent_skill_agent_sort (agent_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;