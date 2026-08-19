---
schemaVersion: 1
name: 浏览器 Notebook
description: 在浏览器 Pyodide 中执行可复核的轻量分析步骤
version: 1.0.0
entrypoints:
  - name: summarize_values
    runtime: pyodide
    script: scripts/entrypoint.py
    description: Produce a small reproducible numeric summary
---

使用 `summarize_values` 对调用参数中明确提供的数值进行统计，不访问浏览器外部数据。
