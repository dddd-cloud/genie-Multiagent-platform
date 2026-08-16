ALTER TABLE conversation
    ADD COLUMN privacy_mode TINYINT(1) NOT NULL DEFAULT 0 AFTER title;
