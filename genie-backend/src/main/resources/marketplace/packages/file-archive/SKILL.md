---
schemaVersion: 1
name: 文件 ZIP 打包器
description: 在浏览器 Python 中把明确提供的文本或 Base64 文件安全打包为 ZIP
version: 1.0.0
entrypoints:
  - name: create_zip
    runtime: pyodide
    script: scripts/entrypoint.py
    description: Create a downloadable ZIP from up to 20 explicitly supplied files
---

当用户需要把多个生成结果整理成一个 ZIP 时调用 `create_zip`。
每个文件必须提供安全的相对文件名和 `contentText` 或 `contentBase64`；拒绝绝对路径、目录穿越、重名和超限内容。
