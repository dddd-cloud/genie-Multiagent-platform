---
schemaVersion: 1
name: joyagent-skill-author
description: Author and validate JoyAgent Skill Packages. Adapted from anthropics/skills skill-creator, rewritten for this repo's SkillManifestParser, SkillPackageValidator, and browser Pyodide runtime. Use when creating or reviewing SKILL.md packages.
version: 1.0.0
entrypoints:
  - name: main
    runtime: pyodide
    script: scripts/run.py
    description: Validate a Skill Package against JoyAgent source rules
    packages: []
---

# JoyAgent Skill Author

Source adapted from [anthropics/skills skill-creator](https://github.com/anthropics/skills/tree/main/skills/skill-creator).
This package is **not** a verbatim Claude Code skill. JoyAgent only executes `pyodide` entrypoints; `python`/`node` runtimes are declared but raise `SKILL_ENTRYPOINT_NOT_AVAILABLE`.

## When to use

- User wants a new Skill Package for JoyAgent Phase2 (`POST /api/v2/skills/import`).
- User pastes a `SKILL.md` or file list and needs a deterministic check before import.

## Call the script

Run entrypoint `main` with JSON:

```json
{
  "skillMd": "---\\nschemaVersion: 1\\nname: demo\\ndescription: demo skill\\nversion: 1.0.0\\n---\\n\\n# Demo\\nInstructions here.",
  "files": ["SKILL.md", "scripts/run.py"]
}
```

The script mirrors:

- `SkillManifestParser` (`genie-backend/.../SkillManifestParser.java`)
- `SkillPackageValidator` (`.../SkillPackageValidator.java`)
- `pyodide.worker.ts` which requires `def main(input):` (not `run`)

## Package layout this product accepts

Only these top-level names survive import:

- `SKILL.md` (required)
- `scripts/`
- `references/`
- `templates/`
- `assets/`

Rejected: `.env`, `credentials.json`, `*.pem`, `*.key`, path traversal, absolute paths.

## Frontmatter rules

Must start with `---\n` and close with `\n---\n`. Required scalars: `name`, `description`. `schemaVersion` defaults to `1`. Nested maps like Anthropic `metadata:` are skipped.

Entrypoint fields:

- `name`, `runtime`, `script` (must live under `scripts/`)
- `runtime: pyodide` is the only executable runtime
- `packages:` only valid for pyodide; no `http`, `git+`, `.whl`, `@`, `/`

## Import path

Zip the folder and `POST /api/v2/skills/import` as `multipart/form-data` field `file`. Max zip 10 MiB. Instruction body max 20000 code points. Packages land at `skills/users/{tenantId}/{ownerId}/{skillId}/`.
