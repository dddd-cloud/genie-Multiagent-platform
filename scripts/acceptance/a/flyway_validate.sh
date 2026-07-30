#!/usr/bin/env bash
exec "$(dirname "$0")/_run_maven_acceptance.sh" "flyway_validate.sh" "MySqlFlywayMigrationTest"
