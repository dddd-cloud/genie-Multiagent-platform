import csv
import html
import io


def create_bar_chart(csv_text="", label_column="", value_column="", title="CSV Chart"):
    rows = list(csv.DictReader(io.StringIO(csv_text)))
    if not rows:
        return {"ok": False, "error": "CSV contains no data rows"}
    fields = list(rows[0].keys())
    if label_column not in fields or value_column not in fields:
        return {"ok": False, "error": "label_column or value_column is missing", "fields": fields}
    values = []
    for row in rows[:30]:
        try:
            values.append((str(row.get(label_column, "")), float(row.get(value_column, ""))))
        except (TypeError, ValueError):
            continue
    if not values:
        return {"ok": False, "error": "No numeric values found in value_column"}
    width, height = 900, max(280, 90 + len(values) * 32)
    maximum = max(abs(number) for _, number in values) or 1
    chart_width = 620
    pieces = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">',
              '<rect width="100%" height="100%" fill="#ffffff"/>',
              f'<text x="24" y="36" font-family="system-ui" font-size="22" font-weight="700">{html.escape(str(title))}</text>']
    for index, (label, number) in enumerate(values):
        y = 66 + index * 32
        bar_width = round(abs(number) / maximum * chart_width, 2)
        pieces.append(f'<text x="24" y="{y + 17}" font-family="system-ui" font-size="13">{html.escape(label[:32])}</text>')
        pieces.append(f'<rect x="220" y="{y}" width="{bar_width}" height="22" rx="4" fill="#1677ff"/>')
        pieces.append(f'<text x="{230 + bar_width}" y="{y + 17}" font-family="system-ui" font-size="13">{number:g}</text>')
    pieces.append("</svg>")
    content = "".join(pieces)
    return {"ok": True, "filename": "chart.svg", "mimeType": "image/svg+xml", "content": content,
            "itemCount": len(values), "minimum": min(number for _, number in values),
            "maximum": max(number for _, number in values)}
