import html


def render_report(title="运行报告", rows=None):
    rows = rows or []
    items = "".join(f"<li>{html.escape(str(row))}</li>" for row in rows)
    document = f"<!doctype html><html><body><h1>{html.escape(str(title))}</h1><ul>{items}</ul></body></html>"
    return {"filename": "report.html", "mimeType": "text/html", "html": document}
