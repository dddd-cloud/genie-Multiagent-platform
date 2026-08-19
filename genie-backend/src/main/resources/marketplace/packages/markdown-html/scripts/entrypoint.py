import html
import re


def _inline(value):
    safe = html.escape(value)
    safe = re.sub(r"`([^`]+)`", r"<code>\1</code>", safe)
    safe = re.sub(r"\*\*([^*]+)\*\*", r"<strong>\1</strong>", safe)
    safe = re.sub(r"(?<!\*)\*([^*]+)\*(?!\*)", r"<em>\1</em>", safe)
    return safe


def render_markdown(markdown="", title="Markdown Preview"):
    blocks = []
    in_code = False
    code_lines = []
    list_open = False
    for raw in str(markdown).splitlines():
        line = raw.rstrip()
        if line.startswith("```"):
            if in_code:
                blocks.append("<pre><code>" + html.escape("\n".join(code_lines)) + "</code></pre>")
                code_lines = []
                in_code = False
            else:
                if list_open:
                    blocks.append("</ul>")
                    list_open = False
                in_code = True
            continue
        if in_code:
            code_lines.append(line)
            continue
        if line.startswith(("- ", "* ")):
            if not list_open:
                blocks.append("<ul>")
                list_open = True
            blocks.append("<li>" + _inline(line[2:].strip()) + "</li>")
            continue
        if list_open:
            blocks.append("</ul>")
            list_open = False
        if not line.strip():
            continue
        heading = re.match(r"^(#{1,6})\s+(.+)$", line)
        if heading:
            level = len(heading.group(1))
            blocks.append(f"<h{level}>{_inline(heading.group(2))}</h{level}>")
        elif line.startswith("> "):
            blocks.append("<blockquote>" + _inline(line[2:]) + "</blockquote>")
        else:
            blocks.append("<p>" + _inline(line) + "</p>")
    if in_code:
        blocks.append("<pre><code>" + html.escape("\n".join(code_lines)) + "</code></pre>")
    if list_open:
        blocks.append("</ul>")
    document = """<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">
<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>{}</title>
<style>body{{max-width:860px;margin:40px auto;padding:0 24px;font:16px/1.7 system-ui;color:#172033}}h1,h2,h3{{line-height:1.25}}pre{{padding:16px;background:#f5f7fa;overflow:auto;border-radius:8px}}code{{font-family:ui-monospace,monospace}}blockquote{{margin-left:0;padding-left:16px;border-left:4px solid #91a4c7;color:#52627a}}</style>
</head><body>{}</body></html>""".format(html.escape(str(title)), "\n".join(blocks))
    return {"filename": "markdown-preview.html", "mimeType": "text/html", "html": document}
