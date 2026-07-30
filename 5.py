#!/usr/bin/env python3
"""Export this repository into two AI-readable text files.

The default output directory is ``results/code``:

* ``project_structure.txt`` contains a compact project tree.
* ``all_project_code.txt`` contains authored text source and documentation.

Generated dependencies, build output, VCS data, lockfiles, binary assets, and
local environment files are intentionally omitted.
"""

from __future__ import annotations

import argparse
import os
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, TextIO


FULLY_EXCLUDED_ROOT_DIRS = {
    "evidence",
}

TREE_EXCLUDED_DIR_NAMES = {
    ".git",
    ".idea",
    ".cursor",
    ".vscode",
    "__pycache__",
    ".pytest_cache",
    ".mypy_cache",
    ".ruff_cache",
    ".venv",
    "venv",
    "node_modules",
    ".pnpm-store",
    ".npm",
    ".yarn",
    ".gradle",
    "target",
    "dist",
    "build",
    "coverage",
    "htmlcov",
    "out",
    ".turbo",
    ".next",
    ".cache",
    "results",
}

TREE_STUB_DIRS = {
    ".git": "VCS history omitted",
    "node_modules": "dependencies omitted",
    ".pnpm-store": "pnpm package store omitted",
    ".npm": "npm cache omitted",
    ".yarn": "Yarn cache omitted",
    ".gradle": "Gradle cache omitted",
    "target": "Maven output omitted",
    "dist": "frontend output omitted",
    "build": "build output omitted",
    "results": "export output omitted",
    "docs/img": "documentation images omitted",
}

CODE_EXCLUDED_DIR_NAMES = {
    ".acceptance-staging",
    ".artifacts",
    ".agents",
    ".audit-venv",
    ".cache",
    ".cursor",
    ".git",
    ".idea",
    ".mypy_cache",
    ".npm",
    ".next",
    ".nuxt",
    ".output",
    ".pytest_cache",
    ".pnpm-store",
    ".ruff_cache",
    ".serverless",
    ".svelte-kit",
    ".tmp",
    ".tmp-m2-local",
    ".tmp-m2-stage",
    ".tox",
    ".turbo",
    ".venv",
    ".vercel",
    ".vscode",
    ".yarn",
    ".gradle",
    "__pycache__",
    "acceptance_artifacts",
    "artifacts",
    "build",
    "coverage",
    "dist",
    "htmlcov",
    "node_modules",
    "out",
    "results",
    "target",
    "temp",
    "tmp",
    "vendor",
    "venv",
    "file_db_dir",
    "img",
    "relayFonts",
    "icon",
}

PATH_CODE_KEEP_PREFIXES = (
    "scripts/acceptance/evidence/",
)

CODE_EXTENSIONS = {
    ".asm", ".c", ".cc", ".clj", ".cljs", ".cmake", ".coffee", ".cpp",
    ".cs", ".cxx", ".dart", ".ex", ".exs", ".fs", ".fsx", ".go",
    ".groovy", ".h", ".hh", ".hpp", ".hxx", ".java", ".jl", ".kt",
    ".kts", ".lua", ".m", ".mm", ".php", ".pl", ".pm", ".py", ".pyi",
    ".pyw", ".r", ".rb", ".rs", ".scala", ".sc", ".sol", ".swift",
    ".tcl", ".vb",
    ".astro", ".css", ".htm", ".html", ".js", ".jsx", ".less", ".mjs",
    ".cjs", ".mts", ".cts", ".sass", ".scss", ".svelte", ".ts", ".tsx",
    ".vue",
    ".bash", ".bat", ".cmd", ".fish", ".ps1", ".sh", ".zsh",
    ".cfg", ".conf", ".editorconfig", ".gql", ".gradle", ".graphql",
    ".ini", ".json", ".jsonc", ".ndjson", ".prisma", ".properties",
    ".proto", ".sql", ".toml", ".xml", ".yaml", ".yml",
    ".adoc", ".md", ".mdx", ".rst", ".txt",
}

CODE_SPECIAL_NAMES = {
    ".dockerignore", ".editorconfig", ".env.example", ".env.sample",
    ".env.template", ".env_template", ".eslintignore", ".eslintrc",
    ".gitattributes", ".gitignore", ".npmrc", ".prettierignore",
    ".prettierrc", ".python-version", "CMakeLists.txt", "Containerfile",
    "Dockerfile", "Gemfile", "Justfile", "LICENSE", "Makefile", "NOTICE",
    "NOTICE-Third Party", "Procfile", "Rakefile", "Vagrantfile",
}

CODE_EXCLUDED_FILE_NAMES = {
    ".env", ".env.development", ".env.local", ".env.production", ".env.test",
    ".DS_Store", "api key.txt", "bun.lock", "bun.lockb", "composer.lock",
    "package-lock.json", "Pipfile.lock", "pnpm-lock.yaml", "poetry.lock",
    "uv.lock", "yarn.lock", "mockServiceWorker.js", "3.py", "4.py", "5.py",
}

LOW_VALUE_FILE_PATTERNS = (
    re.compile(r"\.min\.(?:css|js)$", re.IGNORECASE),
    re.compile(r"\.map$", re.IGNORECASE),
    re.compile(r"(?:^|/)sales_data\.sql$", re.IGNORECASE),
)

STRUCTURE_FILE = "project_structure.txt"
CODE_FILE = "all_project_code.txt"


@dataclass
class ExportStats:
    bundled_files: int = 0
    bundled_bytes: int = 0
    skipped_large: int = 0
    skipped_binary: int = 0
    skipped_unreadable: int = 0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--root",
        type=Path,
        default=Path(__file__).resolve().parent,
        help="Repository root (default: directory containing this script).",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=Path("results/code"),
        help="Output directory, relative to root unless absolute.",
    )
    parser.add_argument(
        "--max-file-bytes",
        type=int,
        default=2_000_000,
        help="Skip individual files larger than this many bytes.",
    )
    return parser.parse_args()


def relative_posix(path: Path, root: Path) -> str:
    return path.relative_to(root).as_posix()


def is_within(path: Path, directory: Path) -> bool:
    try:
        path.relative_to(directory)
        return True
    except ValueError:
        return False


def sorted_children(directory: Path) -> list[Path]:
    try:
        children = list(directory.iterdir())
    except (OSError, PermissionError):
        return []
    return sorted(children, key=lambda item: (not item.is_dir(), item.name.casefold()))


def is_fully_excluded_root(path: Path, root: Path) -> bool:
    try:
        relative = path.relative_to(root)
    except ValueError:
        return False
    return len(relative.parts) >= 1 and relative.parts[0] in FULLY_EXCLUDED_ROOT_DIRS


def tree_stub_reason(path: Path, root: Path) -> str | None:
    relative = relative_posix(path, root)
    if relative in TREE_STUB_DIRS:
        return TREE_STUB_DIRS[relative]
    if path.name in TREE_STUB_DIRS:
        return TREE_STUB_DIRS[path.name]
    if path.name in TREE_EXCLUDED_DIR_NAMES:
        return "contents omitted"
    return None


def write_tree(root: Path, output: TextIO) -> None:
    output.write(f"{root.name}/\n")

    def visit(directory: Path, prefix: str) -> None:
        visible = [
            child
            for child in sorted_children(directory)
            if not is_fully_excluded_root(child, root)
        ]
        for index, child in enumerate(visible):
            last = index == len(visible) - 1
            branch = "└── " if last else "├── "
            continuation = "    " if last else "│   "
            if child.is_symlink():
                output.write(f"{prefix}{branch}{child.name} -> [symlink omitted]\n")
                continue
            if child.is_dir():
                reason = tree_stub_reason(child, root)
                if reason:
                    output.write(f"{prefix}{branch}{child.name}/ [{reason}]\n")
                    continue
                output.write(f"{prefix}{branch}{child.name}/\n")
                visit(child, prefix + continuation)
            else:
                output.write(f"{prefix}{branch}{child.name}\n")

    visit(root, "")


def path_has_kept_prefix(relative: str) -> bool:
    return any(relative.startswith(prefix) for prefix in PATH_CODE_KEEP_PREFIXES)


def is_code_candidate(path: Path, root: Path, output_dir: Path) -> bool:
    if path.is_symlink() or not path.is_file():
        return False
    resolved = path.resolve()
    if is_within(resolved, output_dir):
        return False

    relative = relative_posix(path, root)
    if is_fully_excluded_root(path, root):
        return False
    if not path_has_kept_prefix(relative):
        if any(part in CODE_EXCLUDED_DIR_NAMES for part in path.relative_to(root).parts[:-1]):
            return False
    if path.name in CODE_EXCLUDED_FILE_NAMES:
        return False
    if any(pattern.search(relative) for pattern in LOW_VALUE_FILE_PATTERNS):
        return False
    return path.name in CODE_SPECIAL_NAMES or path.suffix.lower() in CODE_EXTENSIONS


def iter_code_files(root: Path, output_dir: Path) -> Iterable[Path]:
    for directory, dir_names, file_names in os.walk(root, followlinks=False):
        current = Path(directory)
        relative_dir = relative_posix(current, root) if current != root else ""

        kept_prefix = path_has_kept_prefix(relative_dir.rstrip("/") + "/")
        dir_names[:] = sorted(
            (
                name
                for name in dir_names
                if not (current == root and name in FULLY_EXCLUDED_ROOT_DIRS)
                and (
                    kept_prefix
                    or name not in CODE_EXCLUDED_DIR_NAMES
                    or path_has_kept_prefix(
                        f"{relative_dir}/{name}/".lstrip("/")
                    )
                )
                and not is_within((current / name).resolve(), output_dir)
            ),
            key=str.casefold,
        )

        for file_name in sorted(file_names, key=str.casefold):
            path = current / file_name
            if is_code_candidate(path, root, output_dir):
                yield path


def read_text_file(path: Path, max_bytes: int, stats: ExportStats) -> str | None:
    try:
        size = path.stat().st_size
    except OSError:
        stats.skipped_unreadable += 1
        return None
    if size > max_bytes:
        stats.skipped_large += 1
        return None

    try:
        data = path.read_bytes()
    except (OSError, PermissionError):
        stats.skipped_unreadable += 1
        return None
    if b"\x00" in data[:8192]:
        stats.skipped_binary += 1
        return None

    try:
        return data.decode("utf-8-sig")
    except UnicodeDecodeError:
        try:
            return data.decode("utf-8", errors="replace")
        except Exception:
            stats.skipped_unreadable += 1
            return None


def write_code_bundle(
    root: Path,
    output_dir: Path,
    output: TextIO,
    max_file_bytes: int,
) -> ExportStats:
    stats = ExportStats()
    output.write(f"PROJECT: {root.name}\n")
    output.write(f"ROOT: {root}\n")
    output.write("FORMAT: each file is enclosed by BEGIN/END FILE markers\n\n")

    for path in iter_code_files(root, output_dir):
        content = read_text_file(path, max_file_bytes, stats)
        if content is None:
            continue
        relative = relative_posix(path, root)
        output.write(f"===== BEGIN FILE: {relative} =====\n")
        output.write(content)
        if content and not content.endswith("\n"):
            output.write("\n")
        output.write(f"===== END FILE: {relative} =====\n\n")
        stats.bundled_files += 1
        stats.bundled_bytes += len(content.encode("utf-8"))

    return stats


def resolve_output_dir(root: Path, configured: Path) -> Path:
    output_dir = configured if configured.is_absolute() else root / configured
    output_dir = output_dir.resolve()
    if output_dir == root:
        raise ValueError("output directory must not be the repository root")
    return output_dir


def main() -> int:
    args = parse_args()
    root = args.root.resolve()
    if not root.is_dir():
        print(f"error: repository root does not exist: {root}", file=sys.stderr)
        return 2
    if args.max_file_bytes <= 0:
        print("error: --max-file-bytes must be positive", file=sys.stderr)
        return 2

    try:
        output_dir = resolve_output_dir(root, args.output_dir)
        output_dir.mkdir(parents=True, exist_ok=True)

        structure_path = output_dir / STRUCTURE_FILE
        code_path = output_dir / CODE_FILE
        with structure_path.open("w", encoding="utf-8", newline="\n") as output:
            write_tree(root, output)
        with code_path.open("w", encoding="utf-8", newline="\n") as output:
            stats = write_code_bundle(
                root,
                output_dir,
                output,
                args.max_file_bytes,
            )
    except (OSError, ValueError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 1

    print(f"structure: {structure_path}")
    print(f"code:      {code_path}")
    print(
        "bundled:   "
        f"{stats.bundled_files} files, {stats.bundled_bytes} UTF-8 bytes"
    )
    print(
        "skipped:   "
        f"{stats.skipped_large} large, "
        f"{stats.skipped_binary} binary, "
        f"{stats.skipped_unreadable} unreadable"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
