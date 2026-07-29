# JoyAgent MVP gate entrypoints (D ownership).
# Acceptance (make mvp-acceptance) is intended for Linux / Git Bash / CI.

SHELL := /bin/bash
.SHELLFLAGS := -eu -o pipefail -c

.PHONY: mvp-unit mvp-integration mvp-ui mvp-e2e mvp-acceptance

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
