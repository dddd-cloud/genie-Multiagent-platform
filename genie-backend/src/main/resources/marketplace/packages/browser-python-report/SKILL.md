---
schemaVersion: 1
name: 浏览器 Python 报告
description: 在浏览器 Pyodide 中生成安全的 HTML 报告
version: 1.0.0
entrypoints:
  - name: render_report
    runtime: pyodide
    script: scripts/entrypoint.py
    description: Render supplied rows as a small HTML report
---

使用 `render_report` 将明确提供的标题和内容转为 HTML。仅处理调用参数，不读取 Cookie、凭据或未授权文件。
