#!/usr/bin/env python3
"""MVP-C 外部验收运行器。所有场景只观察冻结 REST/SSE 契约。"""
from __future__ import annotations

import json
import os
import sys
import time
import uuid
from datetime import datetime, timezone
from http.client import IncompleteRead
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import urlparse
from urllib.request import HTTPCookieProcessor, Request, build_opener
from http.cookiejar import CookieJar


SCENARIO_GATES: dict[str, tuple[str, ...]] = {
    "fake_agent_success": ("C-G2", "C-G3", "C-G5", "C-G7"),
    "fake_agent_500": ("C-G3", "C-G4", "C-G7"),
    "fake_agent_disconnect": ("C-G3", "C-G4", "C-G7"),
    "fake_agent_malformed": ("C-G3", "C-G4", "C-G7"),
    "fake_agent_no_final": ("C-G3", "C-G4", "C-G7"),
    "snapshot_restore": ("C-G5",),
    "snapshot_too_large": ("C-G3", "C-G5", "C-G7"),
    "history_context": ("C-G6",),
    "react_plan_regression": ("C-G6", "C-G9"),
    "client_disconnect": ("C-G3", "C-G8"),
}

BLOCKED_REASONS = frozenset({
    "MISSING_ENVIRONMENT",
    "MISSING_REGRESSION_SCENARIO",
    "PREREQUISITE_UNAVAILABLE",
})

DEFAULT_BASE_URL = "http://127.0.0.1:8080"
DEFAULT_TIMEOUT_SECONDS = 45
DEFAULT_POLL_SECONDS = 20
MAX_STREAM_BYTES = 16 * 1024 * 1024
FROZEN_ACCEPTANCE_USERNAME = "user-a"
REQUIRED_REGRESSION_SCENARIOS = frozenset({"react", "plan", "search", "code", "report", "dataAgent"})
REAL_AGENT_KEY_ENVIRONMENTS = (
    "MVP_REAL_LLM_API_KEY",
    "OPENAI_API_KEY",
    "LLM_API_KEY",
)


def normalize_base_url(value: str) -> str:
    parsed = urlparse(value)
    if (
            parsed.scheme not in {"http", "https"}
            or not parsed.netloc
            or parsed.path not in {"", "/"}
            or parsed.params
            or parsed.query
            or parsed.fragment
    ):
        raise AcceptanceFailure("INVALID_INVOCATION", "base URL must be an absolute HTTP(S) origin")
    return value.rstrip("/")


def result_document(
        *,
        command: str,
        scenario: str,
        started_at: str,
        exit_code: int,
        status: str,
        reason: str,
        message: str,
        checks: list[dict[str, Any]],
        details: dict[str, Any],
) -> str:
    return json.dumps({
        "command": command,
        "exitCode": exit_code,
        "startedAt": started_at,
        "finishedAt": now(),
        "result": {
            "status": status,
            "scenario": scenario,
            "gateIds": list(SCENARIO_GATES.get(scenario, ())),
            "reason": reason,
            "message": message,
            "checks": checks,
            "details": details,
        },
    }, ensure_ascii=False, separators=(",", ":"))


class AcceptanceFailure(Exception):
    def __init__(self, reason: str, message: str, details: dict[str, Any] | None = None):
        super().__init__(message)
        self.reason = reason
        self.message = message
        self.details = details or {}


def now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")


class AcceptanceRun:
    def __init__(
            self,
            scenario: str,
            command: str,
            base_url: str,
            regression_scenarios_path: str | None,
            real_agent_enabled: bool,
    ):
        self.scenario = scenario
        self.command = Path(command).name
        self.started_at = now()
        self.checks: list[dict[str, Any]] = []
        self.base_url = normalize_base_url(base_url)
        self.regression_scenarios_path = regression_scenarios_path
        self.real_agent_enabled = real_agent_enabled
        self.timeout = DEFAULT_TIMEOUT_SECONDS
        self.poll_seconds = DEFAULT_POLL_SECONDS
        self.max_stream_bytes = MAX_STREAM_BYTES
        self.opener = build_opener(HTTPCookieProcessor(CookieJar()))
        self.csrf_header = ""
        self.csrf_token = ""

    def required_env(self, name: str) -> str:
        value = os.getenv(name, "")
        if not value:
            raise AcceptanceFailure("MISSING_ENVIRONMENT", f"{name} is required")
        return value

    def check(self, name: str) -> None:
        self.checks.append({"name": name, "passed": True})

    def fail(self, reason: str, message: str, **details: Any) -> None:
        raise AcceptanceFailure(reason, message, details)

    def json_result(self, exit_code: int, status: str, reason: str, message: str, details: dict[str, Any]) -> str:
        return result_document(
            command=self.command,
            scenario=self.scenario,
            started_at=self.started_at,
            exit_code=exit_code,
            status=status,
            reason=reason,
            message=message,
            checks=self.checks,
            details=details,
        )

    def request(self, method: str, path: str, payload: Any | None = None, *, csrf: bool = False,
                accept: str = "application/json", limit: int = 512 * 1024) -> tuple[int, dict[str, str], bytes]:
        headers = {"Accept": accept}
        body = None
        if payload is not None:
            body = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
            headers["Content-Type"] = "application/json"
        if csrf:
            if not self.csrf_header or not self.csrf_token:
                self.fail("AUTHENTICATION_SETUP_FAILED", "CSRF token is unavailable")
            headers[self.csrf_header] = self.csrf_token
        request = Request(self.base_url + path, data=body, headers=headers, method=method)
        try:
            with self.opener.open(request, timeout=self.timeout) as response:
                raw = response.read(limit + 1)
                if len(raw) > limit:
                    self.fail("RESPONSE_TOO_LARGE", "response exceeded the configured acceptance limit")
                return response.status, dict(response.headers.items()), raw
        except HTTPError as error:
            raw = error.read(limit + 1)
            return error.code, dict(error.headers.items()), raw[:limit]
        except IncompleteRead as error:
            return 200, {}, error.partial
        except URLError as error:
            self.fail("PREREQUISITE_UNAVAILABLE", "acceptance service is unreachable", error=type(error.reason).__name__)
        except TimeoutError:
            self.fail("PREREQUISITE_UNAVAILABLE", "acceptance service timed out")

    def json_request(self, method: str, path: str, payload: Any | None = None, *, csrf: bool = False) -> tuple[int, dict[str, Any]]:
        status, _, raw = self.request(method, path, payload, csrf=csrf)
        try:
            node = json.loads(raw.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as error:
            raise AcceptanceFailure("CONTRACT_RESPONSE_INVALID", "REST response is not valid JSON", {"httpStatus": status}) from error
        if not isinstance(node, dict):
            self.fail("CONTRACT_RESPONSE_INVALID", "REST response is not an ApiResponse object", httpStatus=status)
        return status, node

    def require_ok(self, status: int, node: dict[str, Any], step: str) -> dict[str, Any]:
        if (
                status != 200
                or node.get("code") != "OK"
                or node.get("message") != "success"
                or not isinstance(node.get("data"), (dict, list))
        ):
            self.fail("PREREQUISITE_UNAVAILABLE", f"{step} did not return the frozen success envelope", httpStatus=status,
                      apiCode=node.get("code"))
        data = node["data"]
        self.check(step)
        return data

    def health(self) -> None:
        status, _, _ = self.request("GET", "/web/health", accept="*/*", limit=4096)
        if status != 200:
            self.fail("PREREQUISITE_UNAVAILABLE", "acceptance service health check failed", httpStatus=status)
        self.check("health endpoint is reachable")

    def refresh_csrf(self) -> None:
        status, node = self.json_request("GET", "/api/v1/auth/csrf")
        data = self.require_ok(status, node, "CSRF endpoint")
        if not isinstance(data, dict) or not isinstance(data.get("headerName"), str) or not isinstance(data.get("token"), str):
            self.fail("CONTRACT_RESPONSE_INVALID", "CSRF response does not contain headerName and token")
        self.csrf_header = data["headerName"]
        self.csrf_token = data["token"]

    def authenticate(self) -> None:
        self.health()
        password = self.required_env("MVP_ACCEPTANCE_USER_PASSWORD")
        self.refresh_csrf()
        status, node = self.json_request(
            "POST",
            "/api/v1/auth/login",
            {"username": FROZEN_ACCEPTANCE_USERNAME, "password": password},
            csrf=True,
        )
        self.require_ok(status, node, "login")
        self.refresh_csrf()
        status, node = self.json_request("GET", "/api/v1/users/me")
        self.require_ok(status, node, "authenticated user lookup")

    def create_conversation(self) -> str:
        status, node = self.json_request("POST", "/api/v1/conversations", {"title": "MVP-C acceptance"}, csrf=True)
        data = self.require_ok(status, node, "conversation creation")
        if not isinstance(data, dict) or not isinstance(data.get("id"), str) or not data["id"]:
            self.fail("CONTRACT_RESPONSE_INVALID", "conversation creation did not return an id")
        return data["id"]

    def sse_request(self, conversation_id: str, request_id: str, query: str, deep_think: int, output_style: str) -> Request:
        payload = {
            "sessionId": conversation_id,
            "requestId": request_id,
            "query": query,
            "deepThink": deep_think,
            "outputStyle": output_style,
        }
        body = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        return Request(
            self.base_url + "/web/api/v1/gpt/queryAgentStreamIncr",
            data=body,
            headers={"Accept": "text/event-stream", "Content-Type": "application/json", self.csrf_header: self.csrf_token},
            method="POST",
        )

    def stream(self, conversation_id: str, request_id: str, query: str, deep_think: int, output_style: str) -> list[dict[str, Any]]:
        request = self.sse_request(conversation_id, request_id, query, deep_think, output_style)
        try:
            with self.opener.open(request, timeout=self.timeout) as response:
                if response.status != 200:
                    self.fail("SSE_REQUEST_FAILED", "outer SSE request did not return HTTP 200", httpStatus=response.status)
                raw = response.read(self.max_stream_bytes + 1)
        except HTTPError as error:
            self.fail("SSE_REQUEST_FAILED", "outer SSE request failed before the stream opened", httpStatus=error.code)
        except IncompleteRead as error:
            raw = error.partial
        except URLError as error:
            self.fail("SSE_REQUEST_FAILED", "outer SSE transport failed", error=type(error.reason).__name__)
        if len(raw) > self.max_stream_bytes:
            self.fail("RESPONSE_TOO_LARGE", "SSE response exceeded the configured acceptance limit")
        events = self.parse_sse(raw)
        if not events:
            self.fail("SSE_PROTOCOL_INVALID", "outer SSE response contains no JSON events")
        self.check("outer SSE emitted JSON events")
        return events

    def parse_sse(self, raw: bytes) -> list[dict[str, Any]]:
        text = raw.decode("utf-8", errors="replace").replace("\r\n", "\n")
        events: list[dict[str, Any]] = []
        for block in text.split("\n\n"):
            data = "\n".join(line[5:].lstrip(" ") for line in block.split("\n") if line.startswith("data:"))
            if not data:
                continue
            try:
                node = json.loads(data)
            except json.JSONDecodeError:
                continue
            if isinstance(node, dict):
                events.append(node)
        return events

    def terminal_event(self, events: list[dict[str, Any]], expected_status: str) -> dict[str, Any]:
        business = [event for event in events if event.get("packageType") != "heartbeat"]
        terminal = [event for event in business if event.get("finished") is True]
        if not terminal:
            self.fail("SSE_PROTOCOL_INVALID", "outer SSE response has no terminal event", eventCount=len(events))
        final = terminal[-1]
        if str(final.get("status", "")).lower() != expected_status:
            self.fail("SSE_TERMINAL_MISMATCH", "outer SSE terminal status does not match the scenario", expected=expected_status,
                      observed=final.get("status"), eventCount=len(events))
        self.check(f"outer SSE reached {expected_status} terminal")
        return final

    def messages(self, conversation_id: str) -> list[dict[str, Any]]:
        status, node = self.json_request("GET", f"/api/v1/conversations/{conversation_id}/messages")
        data = self.require_ok(status, node, "message history lookup")
        if not isinstance(data, list):
            self.fail("CONTRACT_RESPONSE_INVALID", "message history data does not match the frozen conversation shape")
        if not all(isinstance(item, dict) for item in data):
            self.fail("CONTRACT_RESPONSE_INVALID", "message history contains a non-object item")
        return data

    def wait_assistant(self, conversation_id: str, request_id: str, status: str, error_code: str | None = None) -> tuple[dict[str, Any], list[dict[str, Any]]]:
        deadline = time.monotonic() + self.poll_seconds
        last_status: Any = None
        while time.monotonic() < deadline:
            records = self.messages(conversation_id)
            assistant = next((item for item in records if item.get("role") == "ASSISTANT" and item.get("requestId") == request_id), None)
            if assistant is not None:
                last_status = assistant.get("status")
                if assistant.get("status") == status:
                    if error_code is not None and assistant.get("errorCode") != error_code:
                        self.fail("PERSISTED_ERROR_MISMATCH", "persisted assistant error code does not match the scenario",
                                  expected=error_code, observed=assistant.get("errorCode"))
                    self.check(f"assistant persisted as {status}")
                    return assistant, records
                if assistant.get("status") in {"COMPLETED", "FAILED", "INTERRUPTED"}:
                    self.fail("PERSISTED_STATE_MISMATCH", "assistant reached an unexpected terminal state", expected=status,
                              observed=assistant.get("status"))
            time.sleep(0.5)
        self.fail("PERSISTED_STATE_TIMEOUT", "assistant did not reach a terminal state before the acceptance timeout",
                  expected=status, observed=last_status)

    def validate_snapshot(self, assistant: dict[str, Any], expected_event_count: int | None = None) -> None:
        raw = assistant.get("streamSnapshot")
        if not isinstance(raw, str):
            self.fail("SNAPSHOT_MISSING", "completed assistant has no Snapshot V1 JSON")
        try:
            snapshot = json.loads(raw)
        except json.JSONDecodeError as error:
            raise AcceptanceFailure("SNAPSHOT_INVALID", "persisted snapshot is not valid JSON") from error
        if (
                not isinstance(snapshot, dict)
                or set(snapshot) != {"payloadVersion", "truncated", "events"}
                or type(snapshot.get("payloadVersion")) is not int
                or snapshot.get("payloadVersion") != 1
                or type(snapshot.get("truncated")) is not bool
        ):
            self.fail("SNAPSHOT_INVALID", "persisted snapshot does not match the frozen V1 envelope")
        events = snapshot["events"]
        if not isinstance(events, list) or any(not isinstance(event, dict) for event in events):
            self.fail("SNAPSHOT_INVALID", "persisted snapshot events are invalid")
        if any(event.get("packageType") == "heartbeat" for event in events):
            self.fail("SNAPSHOT_INVALID", "persisted snapshot contains a heartbeat")
        if expected_event_count is not None and len(events) != expected_event_count:
            self.fail("SNAPSHOT_EVENT_ORDER_MISMATCH", "snapshot event count differs from client-visible business events",
                      expected=expected_event_count, observed=len(events))
        self.check("Snapshot V1 persisted without heartbeat events")

    def ensure_real_agent(self) -> None:
        if not self.real_agent_enabled:
            self.fail("PREREQUISITE_UNAVAILABLE", "--real-agent is required for real-Agent regression")
        if not any(os.getenv(name, "").strip() for name in REAL_AGENT_KEY_ENVIRONMENTS):
            self.fail(
                "MISSING_ENVIRONMENT",
                "a real-Agent API key is required",
                acceptedEnvironmentNames=list(REAL_AGENT_KEY_ENVIRONMENTS),
            )
        self.check("real-Agent regression was explicitly enabled")

    def run_fake(self, terminal: str, persisted_status: str, error_code: str | None = None,
                 snapshot: bool = False) -> dict[str, Any]:
        self.authenticate()
        conversation_id = self.create_conversation()
        request_id = uuid.uuid4().hex
        events = self.stream(conversation_id, request_id, "MVP-C fake acceptance request", 0, "docs")
        self.terminal_event(events, terminal)
        assistant, _ = self.wait_assistant(conversation_id, request_id, persisted_status, error_code)
        if snapshot:
            visible = sum(1 for event in events if event.get("packageType") != "heartbeat")
            self.validate_snapshot(assistant, visible)
        return {"outerEventCount": len(events), "persistedStatus": persisted_status,
                "persistedErrorCode": error_code, "snapshotValidated": snapshot}

    def close_client_after_first_event(self, conversation_id: str, request_id: str) -> None:
        request = self.sse_request(conversation_id, request_id, "MVP-C client disconnect acceptance request", 0, "docs")
        response = None
        try:
            response = self.opener.open(request, timeout=self.timeout)
            if response.status != 200:
                self.fail("SSE_REQUEST_FAILED", "outer SSE request did not return HTTP 200", httpStatus=response.status)
            data_lines: list[str] = []
            while True:
                line = response.readline()
                if not line:
                    break
                decoded = line.decode("utf-8", errors="replace").strip("\r\n")
                if not decoded and data_lines:
                    payload = "\n".join(data_lines)
                    try:
                        event = json.loads(payload)
                    except json.JSONDecodeError:
                        data_lines = []
                        continue
                    if isinstance(event, dict) and event.get("packageType") != "heartbeat":
                        self.check("client observed a non-heartbeat SSE event before disconnect")
                        return
                    data_lines = []
                elif decoded.startswith("data:"):
                    data_lines.append(decoded[5:].lstrip(" "))
            self.fail("SSE_PROTOCOL_INVALID", "stream ended before a client-disconnect probe event was observed")
        except HTTPError as error:
            self.fail("SSE_REQUEST_FAILED", "outer SSE request failed before client-disconnect probing", httpStatus=error.code)
        finally:
            if response is not None:
                response.close()


def fake_success(run: AcceptanceRun) -> dict[str, Any]:
    return run.run_fake("success", "COMPLETED", snapshot=True)


def fake_500(run: AcceptanceRun) -> dict[str, Any]:
    return run.run_fake("failed", "FAILED", "AGENT_DOWNSTREAM_ERROR")


def fake_disconnect(run: AcceptanceRun) -> dict[str, Any]:
    return run.run_fake("failed", "FAILED", "AGENT_NO_FINAL_EVENT")


def fake_malformed(run: AcceptanceRun) -> dict[str, Any]:
    return run.run_fake("failed", "FAILED", "AGENT_STREAM_INTERRUPTED")


def fake_no_final(run: AcceptanceRun) -> dict[str, Any]:
    return run.run_fake("failed", "FAILED", "AGENT_NO_FINAL_EVENT")


def snapshot_restore(run: AcceptanceRun) -> dict[str, Any]:
    details = run.run_fake("success", "COMPLETED", snapshot=True)
    run.check("completed stream can be restored from its persisted Snapshot V1 envelope")
    return details


def snapshot_too_large(run: AcceptanceRun) -> dict[str, Any]:
    return run.run_fake("failed", "FAILED", "SNAPSHOT_TOO_LARGE")


def client_disconnect(run: AcceptanceRun) -> dict[str, Any]:
    run.authenticate()
    conversation_id = run.create_conversation()
    request_id = uuid.uuid4().hex
    run.close_client_after_first_event(conversation_id, request_id)
    assistant, _ = run.wait_assistant(conversation_id, request_id, "INTERRUPTED", "CLIENT_DISCONNECTED")
    if assistant.get("status") == "COMPLETED":
        run.fail("PERSISTED_STATE_MISMATCH", "client disconnect incorrectly completed the assistant")
    run.check("client disconnect did not persist COMPLETED")
    return {"persistedStatus": assistant.get("status"), "persistedErrorCode": assistant.get("errorCode")}


def final_text(event: dict[str, Any]) -> str:
    for field in ("responseAll", "response"):
        value = event.get(field)
        if isinstance(value, str) and value.strip():
            return value
    result_map = event.get("resultMap")
    if not isinstance(result_map, dict):
        return ""
    return structured_final_text(result_map.get("eventData"))


def structured_final_text(value: Any) -> str:
    if isinstance(value, dict):
        for key in ("taskSummary", "result"):
            candidate = value.get(key)
            if isinstance(candidate, str) and candidate.strip():
                return candidate
        for key in sorted(value, key=str):
            candidate = structured_final_text(value[key])
            if candidate:
                return candidate
    elif isinstance(value, list):
        for item in value:
            candidate = structured_final_text(item)
            if candidate:
                return candidate
    return ""


def history_context(run: AcceptanceRun) -> dict[str, Any]:
    run.ensure_real_agent()
    run.authenticate()
    conversation_id = run.create_conversation()
    marker = f"mvp-c-history-{uuid.uuid4().hex[:12]}"
    first_request_id = uuid.uuid4().hex
    first_events = run.stream(
        conversation_id,
        first_request_id,
        f"请记住标识 {marker}，并在回答中原样返回该标识。",
        0,
        "docs",
    )
    run.terminal_event(first_events, "success")
    run.wait_assistant(conversation_id, first_request_id, "COMPLETED")

    second_request_id = uuid.uuid4().hex
    second_events = run.stream(
        conversation_id,
        second_request_id,
        "仅返回上一轮要求记住的标识，不要解释。",
        0,
        "docs",
    )
    second_final = run.terminal_event(second_events, "success")
    assistant, records = run.wait_assistant(conversation_id, second_request_id, "COMPLETED")
    if marker not in final_text(second_final):
        run.fail("HISTORY_CONTEXT_NOT_OBSERVED", "second ReAct answer did not contain the first-turn marker")
    relevant = [item for item in records if item.get("requestId") in {first_request_id, second_request_id}]
    if len(relevant) != 4:
        run.fail("PERSISTED_HISTORY_MISMATCH", "history did not retain two complete user/assistant turns", observed=len(relevant))
    if assistant.get("status") != "COMPLETED":
        run.fail("PERSISTED_STATE_MISMATCH", "second assistant did not complete")
    run.check("second ReAct request observed a prior completed turn")
    run.check("current request remained a distinct completed turn")
    return {"turnCount": 2, "mode": "ReAct"}


def regression_scenarios(run: AcceptanceRun) -> list[dict[str, Any]]:
    if not run.regression_scenarios_path:
        run.fail("MISSING_REGRESSION_SCENARIO", "--regression-scenarios is required for real-Agent regression")
    try:
        raw = Path(run.regression_scenarios_path).read_text(encoding="utf-8")
    except OSError as error:
        raise AcceptanceFailure("MISSING_REGRESSION_SCENARIO", "regression scenario file is unavailable") from error
    try:
        scenarios = json.loads(raw)
    except json.JSONDecodeError as error:
        raise AcceptanceFailure("INVALID_INVOCATION", "regression scenario file must contain a JSON array") from error
    if not isinstance(scenarios, list):
        run.fail("INVALID_INVOCATION", "regression scenario file must contain a JSON array")
    names = {item.get("name") for item in scenarios if isinstance(item, dict)}
    missing = sorted(REQUIRED_REGRESSION_SCENARIOS - names)
    if missing:
        run.fail("MISSING_REGRESSION_SCENARIO", "real-Agent regression scenarios are incomplete", missing=missing)
    for item in scenarios:
        if not isinstance(item, dict):
            run.fail("INVALID_INVOCATION", "each real-Agent regression scenario must be an object")
        for field in ("name", "query", "expectedFragment", "deepThink", "outputStyle"):
            if field not in item:
                run.fail("INVALID_INVOCATION", "real-Agent regression scenario is missing a required field", field=field)
        if not isinstance(item["query"], str) or not item["query"].strip() or not isinstance(item["expectedFragment"], str) or not item["expectedFragment"]:
            run.fail("INVALID_INVOCATION", "real-Agent regression query and expectedFragment must be non-empty strings")
        if item["deepThink"] not in (0, 1) or item["outputStyle"] not in {"dataAgent", "html", "docs", "ppt", "table"}:
            run.fail("INVALID_INVOCATION", "real-Agent regression uses an unsupported frozen request value")
    return scenarios


def react_plan_regression(run: AcceptanceRun) -> dict[str, Any]:
    run.ensure_real_agent()
    scenarios = regression_scenarios(run)
    run.authenticate()
    completed: list[str] = []
    for scenario in scenarios:
        conversation_id = run.create_conversation()
        request_id = uuid.uuid4().hex
        events = run.stream(
            conversation_id,
            request_id,
            scenario["query"],
            scenario["deepThink"],
            scenario["outputStyle"],
        )
        final = run.terminal_event(events, "success")
        run.wait_assistant(conversation_id, request_id, "COMPLETED")
        if scenario["expectedFragment"] not in final_text(final):
            run.fail("REGRESSION_OUTPUT_MISMATCH", "real-Agent regression final answer missed its expected fragment",
                     scenario=scenario["name"])
        completed.append(scenario["name"])
        run.check(f"real-Agent regression completed: {scenario['name']}")
    return {"completedScenarios": completed}


SCENARIOS = {
    "fake_agent_success": fake_success,
    "fake_agent_500": fake_500,
    "fake_agent_disconnect": fake_disconnect,
    "fake_agent_malformed": fake_malformed,
    "fake_agent_no_final": fake_no_final,
    "snapshot_restore": snapshot_restore,
    "snapshot_too_large": snapshot_too_large,
    "history_context": history_context,
    "react_plan_regression": react_plan_regression,
    "client_disconnect": client_disconnect,
}


def parse_invocation(argv: list[str]) -> tuple[str, str, str, str | None, bool]:
    if len(argv) < 3:
        raise AcceptanceFailure("INVALID_INVOCATION", "expected a supported scenario and script path")
    scenario = argv[1]
    if scenario not in SCENARIOS:
        raise AcceptanceFailure("INVALID_INVOCATION", "scenario is not supported")

    script_path = argv[2]
    base_url = DEFAULT_BASE_URL
    regression_scenarios_path: str | None = None
    real_agent_enabled = False
    index = 3
    while index < len(argv):
        option = argv[index]
        if option == "--base-url" and index + 1 < len(argv):
            base_url = argv[index + 1]
            index += 2
        elif option == "--regression-scenarios" and index + 1 < len(argv):
            regression_scenarios_path = argv[index + 1]
            index += 2
        elif option == "--real-agent":
            real_agent_enabled = True
            index += 1
        else:
            raise AcceptanceFailure("INVALID_INVOCATION", "unsupported or incomplete acceptance option")

    if regression_scenarios_path and scenario != "react_plan_regression":
        raise AcceptanceFailure("INVALID_INVOCATION", "--regression-scenarios is only valid for react_plan_regression")
    if real_agent_enabled and scenario not in {"history_context", "react_plan_regression"}:
        raise AcceptanceFailure("INVALID_INVOCATION", "--real-agent is only valid for real-Agent scenarios")
    return scenario, script_path, base_url, regression_scenarios_path, real_agent_enabled


def main(argv: list[str]) -> int:
    command = Path(argv[0]).name
    started_at = now()
    scenario = argv[1] if len(argv) >= 2 else "invalid_invocation"
    run: AcceptanceRun | None = None
    try:
        scenario, script_path, base_url, regression_scenarios_path, real_agent_enabled = parse_invocation(argv)
        run = AcceptanceRun(
            scenario,
            script_path,
            base_url,
            regression_scenarios_path,
            real_agent_enabled,
        )
        details = SCENARIOS[scenario](run)
    except AcceptanceFailure as error:
        print(result_document(
            command=command,
            scenario=scenario,
            started_at=started_at,
            exit_code=2 if error.reason == "INVALID_INVOCATION" else 1,
            status="BLOCKED" if error.reason in BLOCKED_REASONS else "FAIL",
            reason=error.reason,
            message=error.message,
            checks=run.checks if run is not None else [],
            details=error.details,
        ))
        return 2 if error.reason == "INVALID_INVOCATION" else 1
    except Exception:
        print(result_document(
            command=command,
            scenario=scenario,
            started_at=started_at,
            exit_code=1,
            status="FAIL",
            reason="UNEXPECTED_ACCEPTANCE_ERROR",
            message="acceptance runner encountered an unexpected error",
            checks=run.checks if run is not None else [],
            details={},
        ))
        return 1
    print(run.json_result(0, "PASS", "OK", "scenario passed", details))
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))

