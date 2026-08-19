---
schemaVersion: 1
name: JSON 数据工作台
description: 在浏览器 Python 中检查、定位和扁平化 JSON 数据
version: 1.0.0
entrypoints:
  - name: inspect_json
    runtime: pyodide
    script: scripts/entrypoint.py
    description: Inspect JSON structure, select a dotted path, and optionally flatten values
---

当用户需要检查 JSON 字段、数据类型、嵌套结构或提取某个路径时，调用 `inspect_json`。
只处理调用参数中明确提供的数据；输入不是合法 JSON 时返回清晰错误，不猜测或修补原始数据。
