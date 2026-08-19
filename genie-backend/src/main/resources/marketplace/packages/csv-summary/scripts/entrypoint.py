import csv
import io


def summarize_csv(csv_text=""):
    rows = list(csv.DictReader(io.StringIO(csv_text)))
    fields = list(rows[0].keys()) if rows else []
    missing = {field: sum(1 for row in rows if not (row.get(field) or "").strip()) for field in fields}
    return {"rowCount": len(rows), "fields": fields, "missingValues": missing}
