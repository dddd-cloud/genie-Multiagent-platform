# JoyAgent MVP gate entrypoints (D ownership).
# Acceptance (make mvp-acceptance) is intended for Linux / Git Bash / CI.

SHELL := /bin/bash
.SHELLFLAGS := -eu -o pipefail -c

.PHONY: mvp-unit mvp-integration mvp-ui mvp-e2e mvp-acceptance \
	phase2-contract-acceptance phase2-a-acceptance phase2-b-acceptance \
	phase2-c-acceptance phase2-d-acceptance phase2-acceptance \
	phase2-agent-005-acceptance phase2-skill-005-acceptance \
	phase2-memory-ui-005-acceptance phase2-005-acceptance

# Fixed A/B/C script whitelist — plan §18.3. Do not glob arbitrary *.sh.
A_SCRIPTS := \
	mysql_startup.sh \
	flyway_validate.sh \
	auth_session.sh \
	csrf_security.sh \
	internal_token.sh \
	user_admin.sh

B_SCRIPTS := \
	conversation_crud.sh \
	conversation_isolation.sh \
	duplicate_request.sh \
	conversation_busy.sh \
	message_state_machine.sh \
	stale_message_recovery.sh \
	history_context.sh

C_SCRIPTS := \
	fake_agent_success.sh \
	fake_agent_500.sh \
	fake_agent_disconnect.sh \
	fake_agent_malformed.sh \
	fake_agent_no_final.sh \
	snapshot_restore.sh \
	snapshot_too_large.sh \
	history_context.sh \
	react_plan_regression.sh

mvp-unit:
	cd genie-backend && mvn -q test
	cd ui && pnpm test

mvp-integration:
	@status=0; \
	run_list() { \
	  owner="$$1"; shift; \
	  dir="scripts/acceptance/$$owner"; \
	  if [ ! -d "$$dir" ]; then \
	    echo "BLOCKED: $$dir missing — $$owner owner must provide scripts"; status=1; return; \
	  fi; \
	  missing=0; \
	  for name in "$$@"; do \
	    s="$$dir/$$name"; \
	    if [ ! -f "$$s" ]; then \
	      echo "BLOCKED: missing $$s"; missing=1; status=1; continue; \
	    fi; \
	    echo "==> $$s"; \
	    if ! bash "$$s"; then echo "FAIL: $$s"; status=1; fi; \
	  done; \
	  if [ "$$missing" -eq 1 ]; then \
	    echo "BLOCKED: $$owner whitelist incomplete"; \
	  fi; \
	}; \
	run_list a $(A_SCRIPTS); \
	run_list b $(B_SCRIPTS); \
	run_list c $(C_SCRIPTS); \
	exit $$status

mvp-ui:
	cd ui && pnpm contract:validate && pnpm install --frozen-lockfile && pnpm lint && pnpm typecheck && pnpm test && pnpm build

mvp-e2e:
	cd ui && MVP_E2E_READY=1 pnpm exec playwright test

mvp-acceptance:
	bash scripts/acceptance/run_all.sh

phase2-contract-acceptance:
	bash scripts/acceptance/phase2/contract/run.sh

phase2-a-acceptance:
	@test -f scripts/acceptance/phase2/a/run.sh || \
	  { echo "BLOCKED: scripts/acceptance/phase2/a/run.sh missing"; exit 2; }
	bash scripts/acceptance/phase2/a/run.sh

phase2-b-acceptance:
	@test -f scripts/acceptance/phase2/b/run.sh || \
	  { echo "BLOCKED: scripts/acceptance/phase2/b/run.sh missing"; exit 2; }
	bash scripts/acceptance/phase2/b/run.sh

phase2-c-acceptance:
	@test -f scripts/acceptance/phase2/c/run.sh || \
	  { echo "BLOCKED: scripts/acceptance/phase2/c/run.sh missing"; exit 2; }
	bash scripts/acceptance/phase2/c/run.sh

phase2-d-acceptance:
	@test -f scripts/acceptance/phase2/d/run.sh || \
	  { echo "BLOCKED: scripts/acceptance/phase2/d/run.sh missing"; exit 2; }
	bash scripts/acceptance/phase2/d/run.sh

phase2-acceptance:
	@test -f scripts/acceptance/phase2/run_all.sh || \
	  { echo "BLOCKED: scripts/acceptance/phase2/run_all.sh missing"; exit 2; }
	bash scripts/acceptance/phase2/run_all.sh

phase2-agent-005-acceptance:
	@test -f scripts/acceptance/phase2/agent/run.sh || \
	  { echo "BLOCKED: scripts/acceptance/phase2/agent/run.sh missing"; exit 2; }
	bash scripts/acceptance/phase2/agent/run.sh

phase2-skill-005-acceptance:
	@test -f scripts/acceptance/phase2/skill/run.sh || \
	  { echo "BLOCKED: scripts/acceptance/phase2/skill/run.sh missing"; exit 2; }
	bash scripts/acceptance/phase2/skill/run.sh

phase2-memory-ui-005-acceptance:
	@test -f scripts/acceptance/phase2/memory-ui/run.sh || \
	  { echo "BLOCKED: scripts/acceptance/phase2/memory-ui/run.sh missing"; exit 2; }
	bash scripts/acceptance/phase2/memory-ui/run.sh

phase2-005-acceptance: mvp-acceptance phase2-contract-acceptance phase2-b-acceptance \
	phase2-agent-005-acceptance phase2-skill-005-acceptance phase2-memory-ui-005-acceptance
