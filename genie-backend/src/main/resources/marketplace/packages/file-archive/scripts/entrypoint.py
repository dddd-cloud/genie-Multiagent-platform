import base64
import io
import posixpath
import zipfile


def _safe_name(name):
    value = str(name or "").replace("\\", "/").strip()
    normalized = posixpath.normpath(value)
    if not value or value.startswith("/") or normalized.startswith("../") or normalized in (".", ".."):
        raise ValueError(f"Unsafe archive path: {value}")
    return normalized


def create_zip(files=None, filename="generated-files.zip"):
    items = files or []
    if not isinstance(items, list) or not 1 <= len(items) <= 20:
        return {"ok": False, "error": "files must contain between 1 and 20 items"}
    output = io.BytesIO()
    names = set()
    total = 0
    try:
        with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED) as archive:
            for item in items:
                name = _safe_name(item.get("name"))
                if name in names:
                    raise ValueError(f"Duplicate archive path: {name}")
                names.add(name)
                if item.get("contentBase64") is not None:
                    content = base64.b64decode(item.get("contentBase64"), validate=True)
                else:
                    content = str(item.get("contentText", "")).encode("utf-8")
                total += len(content)
                if total > 2 * 1024 * 1024:
                    raise ValueError("Archive input exceeds 2 MiB")
                archive.writestr(name, content)
        payload = output.getvalue()
        return {"ok": True, "filename": _safe_name(filename), "mimeType": "application/zip",
                "contentBase64": base64.b64encode(payload).decode("ascii"),
                "fileCount": len(names), "byteCount": len(payload)}
    except (ValueError, TypeError, base64.binascii.Error) as error:
        return {"ok": False, "error": str(error)}
