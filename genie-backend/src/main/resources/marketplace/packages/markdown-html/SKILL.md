---
schemaVersion: 1
name: Markdown 网页生成器
description: 在浏览器 Python 中把安全 Markdown 子集生成可预览 HTML
version: 1.0.0
entrypoints:
  - name: render_markdown
    runtime: pyodide
    script: scripts/entrypoint.py
    description: Render headings, paragraphs, lists, quotes and code blocks as standalone HTML
---

当用户希望把 Markdown 内容制作成网页时调用 `render_markdown`。
生成器会转义原始 HTML，只渲染常用 Markdown 结构，并返回可预览、可下载的独立 HTML 文件。
