import json


def _summary(value):
    counts = {"objects": 0, "arrays": 0, "strings": 0, "numbers": 0, "booleans": 0, "nulls": 0}

    def visit(item):
        if isinstance(item, dict):
            counts["objects"] += 1
            for child in item.values():
                visit(child)
        elif isinstance(item, list):
            counts["arrays"] += 1
            for child in item:
                visit(child)
        elif isinstance(item, bool):
            counts["booleans"] += 1
        elif item is None:
            counts["nulls"] += 1
        elif isinstance(item, (int, float)):
            counts["numbers"] += 1
        else:
            counts["strings"] += 1
    visit(value)
    return counts


def _select(value, path):
    current = value
    if not path:
        return current
    for part in str(path).split("."):
        if isinstance(current, list):
            current = current[int(part)]
        elif isinstance(current, dict) and part in current:
            current = current[part]
        else:
            raise ValueError(f"JSON path not found: {path}")
    return current


def _flatten(value, prefix="", output=None):
    output = output if output is not None else {}
    if len(output) >= 500:
        return output
    if isinstance(value, dict):
        for key, child in value.items():
            _flatten(child, f"{prefix}.{key}" if prefix else str(key), output)
    elif isinstance(value, list):
        for index, child in enumerate(value):
            _flatten(child, f"{prefix}.{index}" if prefix else str(index), output)
    else:
        output[prefix or "$root"] = value
    return output


def inspect_json(json_text="", path="", flatten=False):
    try:
        value = json.loads(json_text)
        selected = _select(value, path)
        result = {
            "ok": True,
            "rootType": type(value).__name__,
            "topLevelKeys": list(value.keys())[:100] if isinstance(value, dict) else [],
            "summary": _summary(value),
            "selectedPath": path or "$root",
            "selected": selected,
        }
        if flatten:
            result["flattened"] = _flatten(selected)
        return result
    except (json.JSONDecodeError, ValueError, TypeError, IndexError) as error:
        return {"ok": False, "error": str(error)}


def main(input):
    """Worker entrypoint; keep inspect_json importable for direct package tests."""
    if hasattr(input, "to_py"):
        input = input.to_py()
    payload = input if isinstance(input, dict) else {}
    return inspect_json(
        json_text=payload.get("json_text", payload.get("json", "")),
        path=payload.get("path", ""),
        flatten=bool(payload.get("flatten", False)),
    )
