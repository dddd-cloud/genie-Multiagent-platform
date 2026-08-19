---
schemaVersion: 1
name: CSV 图表生成器
description: 在浏览器 Python 中把 CSV 分类数值生成可下载 SVG 柱状图
version: 1.0.0
entrypoints:
  - name: create_bar_chart
    runtime: pyodide
    script: scripts/entrypoint.py
    description: Build an SVG bar chart from selected CSV label and numeric columns
---

当用户需要把 CSV 中的分类和数值列快速画成柱状图时，调用 `create_bar_chart`。
必须明确 label_column 和 value_column；最多绘制 30 行，并同时返回实际使用的数据摘要。
