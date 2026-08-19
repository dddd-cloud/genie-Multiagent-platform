---
schemaVersion: 1
name: CSV 数据摘要
description: 用 Python 标准库生成 CSV 字段和缺失值摘要
version: 1.0.0
entrypoints:
  - name: summarize_csv
    runtime: pyodide
    script: scripts/entrypoint.py
    description: Summarize supplied CSV text
---

使用 `summarize_csv` 处理调用参数中明确提供的 CSV 文本，返回结构化数据质量结果。
