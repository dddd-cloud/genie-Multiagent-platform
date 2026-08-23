ALTER TABLE conversation
    ADD COLUMN workspace_id VARCHAR(36) NULL AFTER privacy_mode;

CREATE INDEX idx_conversation_workspace ON conversation (owner_id, workspace_id);
