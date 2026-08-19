---
schemaVersion: 1
name: 创建 PDF 报告
description: 在浏览器 Pyodide 中创建基础 PDF 文件
version: 1.0.0
entrypoints:
  - name: create_pdf
    runtime: pyodide
    script: scripts/entrypoint.py
    description: Create a minimal downloadable PDF from title and text lines
---

使用 `create_pdf` 生成仅包含调用参数内容的基础 PDF。返回 Base64 文件内容，调用方负责提供下载或预览界面。
