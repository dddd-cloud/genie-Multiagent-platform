import base64


def _escape_pdf(value):
    return str(value).replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")


def create_pdf(title="Skill Report", lines=None):
    text_lines = [str(title)] + [str(line) for line in (lines or [])]
    commands = ["BT", "/F1 16 Tf", "72 760 Td"]
    for index, line in enumerate(text_lines):
        if index:
            commands.append("0 -24 Td")
        commands.append(f"({_escape_pdf(line)}) Tj")
    commands.append("ET")
    stream = "\n".join(commands).encode("latin-1", "replace")
    objects = [
        b"<< /Type /Catalog /Pages 2 0 R >>",
        b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
        b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
        b"<< /Length " + str(len(stream)).encode() + b" >>\nstream\n" + stream + b"\nendstream",
    ]
    pdf = bytearray(b"%PDF-1.4\n")
    offsets = [0]
    for number, value in enumerate(objects, 1):
        offsets.append(len(pdf))
        pdf.extend(f"{number} 0 obj\n".encode())
        pdf.extend(value)
        pdf.extend(b"\nendobj\n")
    xref = len(pdf)
    pdf.extend(f"xref\n0 {len(objects) + 1}\n0000000000 65535 f \n".encode())
    for offset in offsets[1:]:
        pdf.extend(f"{offset:010d} 00000 n \n".encode())
    pdf.extend(f"trailer\n<< /Size {len(objects) + 1} /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF".encode())
    return {"filename": "report.pdf", "mimeType": "application/pdf", "contentBase64": base64.b64encode(pdf).decode("ascii")}
