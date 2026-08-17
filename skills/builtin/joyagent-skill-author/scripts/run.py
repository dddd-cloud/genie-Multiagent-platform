"""Validate a Skill Package against JoyAgent SkillManifestParser / SkillPackageValidator."""

ALLOWED_TOP = {"SKILL.md", "scripts", "references", "templates", "assets"}
SENSITIVE = {
    ".env",
    ".env.local",
    "credentials",
    "credentials.json",
    "credential.json",
    "secrets",
    "secrets.json",
    "secret.json",
    "id_rsa",
    "id_ed25519",
}
EXECUTABLE_RUNTIME = "pyodide"
MAX_INSTRUCTION = 20000


def _scalar(value):
    result = value.strip()
    if len(result) >= 2 and (
        (result.startswith('"') and result.endswith('"'))
        or (result.startswith("'") and result.endswith("'"))
    ):
        result = result[1:-1]
    return result.strip()


def parse_frontmatter(source):
    errors = []
    text = source.replace("\r\n", "\n")
    if text.startswith("\ufeff"):
        text = text[1:]
    if not text.startswith("---\n"):
        return None, ["SKILL.md frontmatter required"]
    close = text.find("\n---\n", 4)
    if close < 0:
        return None, ["SKILL.md frontmatter not closed"]
    body = text[close + 5 :].strip()
    if not body:
        errors.append("SKILL.md instruction required")
    if len(body) > MAX_INSTRUCTION:
        errors.append("instruction exceeds 20000 code points")
    root = {}
    entries = []
    current = None
    in_entrypoints = False
    in_packages = False
    skipping = False
    for raw in text[4:close].split("\n"):
        if not raw.strip() or raw.lstrip().startswith("#"):
            continue
        indent = len(raw) - len(raw.lstrip())
        line = raw.strip()
        if indent == 0:
            current = None
            in_packages = False
            skipping = False
            if line == "entrypoints:":
                in_entrypoints = True
                continue
            in_entrypoints = False
            colon = line.find(":")
            if colon > 0 and not line[colon + 1 :].strip():
                skipping = True
                continue
            if colon <= 0:
                errors.append("invalid frontmatter scalar: " + line)
                continue
            key = line[:colon].strip()
            if key in root:
                errors.append("duplicate frontmatter field: " + key)
            else:
                root[key] = _scalar(line[colon + 1 :])
        elif skipping:
            continue
        elif in_entrypoints and indent == 2 and line.startswith("- "):
            current = {"values": {}, "packages": []}
            entries.append(current)
            in_packages = False
            rest = line[2:]
            colon = rest.find(":")
            if colon > 0:
                current["values"][_scalar(rest[:colon])] = _scalar(rest[colon + 1 :])
        elif current is not None and indent == 4 and line == "packages:":
            in_packages = True
        elif current is not None and indent == 6 and in_packages and line.startswith("- "):
            current["packages"].append(_scalar(line[2:]))
        elif current is not None and indent == 4:
            in_packages = False
            colon = line.find(":")
            if colon > 0:
                current["values"][line[:colon].strip()] = _scalar(line[colon + 1 :])
        else:
            errors.append("invalid frontmatter indentation: " + raw)
    schema = root.get("schemaVersion", "1")
    if schema != "1":
        errors.append("unsupported schemaVersion")
    for key in ("name", "description"):
        if not root.get(key):
            errors.append("missing " + key)
    return {
        "root": root,
        "body": body,
        "entrypoints": entries,
    }, errors


def validate_files(files):
    errors = []
    for path in files:
        portable = str(path).replace("\\", "/")
        if portable.startswith("/") or (len(portable) > 1 and portable[1] == ":"):
            errors.append("absolute path rejected: " + portable)
            continue
        parts = portable.split("/")
        if ".." in parts:
            errors.append("path traversal rejected: " + portable)
            continue
        top = parts[0]
        if top not in ALLOWED_TOP:
            errors.append("unsupported package path: " + portable)
        for part in parts:
            name = part.lower()
            if name in SENSITIVE or name.startswith(".env.") or name.endswith(
                (".pem", ".key", ".p12", ".pfx")
            ):
                errors.append("sensitive path rejected: " + portable)
    if "SKILL.md" not in files:
        errors.append("SKILL.md missing")
    return errors


def validate_entrypoints(entries, files):
    errors = []
    warnings = []
    for entry in entries:
        values = entry.get("values") or {}
        runtime = values.get("runtime")
        script = values.get("script")
        name = values.get("name")
        if not name:
            errors.append("missing entrypoint name")
        if not runtime:
            errors.append("missing entrypoint runtime")
        elif runtime != EXECUTABLE_RUNTIME:
            warnings.append(
                "runtime %s is not executable in JoyAgent; only pyodide runs in the browser"
                % runtime
            )
        if not script:
            errors.append("missing entrypoint script")
        else:
            portable = script.replace("\\", "/")
            if not portable.startswith("scripts/"):
                errors.append("script must be under scripts/: " + portable)
            if portable not in files:
                errors.append("entrypoint script not in file list: " + portable)
        packages = entry.get("packages") or []
        if packages and runtime != "pyodide":
            errors.append("packages are only valid for pyodide")
        for spec in packages:
            lower = spec.lower()
            if (
                "http" in lower
                or "git+" in lower
                or lower.endswith(".whl")
                or any(ch in spec for ch in "@/\\:")
            ):
                errors.append("invalid pyodide package spec: " + spec)
    if not entries:
        warnings.append(
            "no entrypoints: this becomes a LEGACY_SYNTHETIC prompt-only skill until a pyodide script is added"
        )
    return errors, warnings


def main(input):
    payload = input if isinstance(input, dict) else {}
    skill_md = payload.get("skillMd") or payload.get("skill_md") or ""
    files = payload.get("files") or ["SKILL.md"]
    if isinstance(files, str):
        files = [item.strip() for item in files.split(",") if item.strip()]
    file_errors = validate_files(files)
    parsed, parse_errors = parse_frontmatter(skill_md) if skill_md else (
        None,
        ["skillMd is required"],
    )
    entry_errors, warnings = ([], [])
    if parsed:
        entry_errors, warnings = validate_entrypoints(parsed.get("entrypoints") or [], files)
        warnings.append(
            "pyodide.worker.ts requires def main(input) in the script; the YAML entrypoint name can still be main"
        )
    errors = file_errors + parse_errors + entry_errors
    return {
        "ok": len(errors) == 0,
        "errors": errors,
        "warnings": warnings,
        "name": (parsed or {}).get("root", {}).get("name"),
        "entrypointCount": len((parsed or {}).get("entrypoints") or []),
        "sourceRules": [
            "SkillManifestParser.java",
            "SkillPackageValidator.java",
            "ui/src/features/phase2/skillRuntime/pyodide.worker.ts",
        ],
    }
