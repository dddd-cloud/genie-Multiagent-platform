# Skill Packages

`GENIE_SKILL_ROOT` defaults to `./skills`. User packages live under
`users/{tenantId}/{ownerId}/{skillId}` and are intentionally ignored by Git.
`builtin/` contains deployment/development examples only; it is not imported
into `agent_skill_binding`.

Stage 1 deterministic limits are defined in `SkillPackageLimits`: SKILL.md 256
KiB, one resource 2 MiB, 256 files, total package 16 MiB, future bundle 20 MiB,
input JSON 1 MiB, output JSON 2 MiB, and stdout/stderr 256 KiB each.
