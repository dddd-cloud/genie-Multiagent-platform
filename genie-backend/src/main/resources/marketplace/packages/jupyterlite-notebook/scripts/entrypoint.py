def summarize_values(values=None):
    values = [float(value) for value in (values or [])]
    if not values:
        return {"count": 0, "sum": 0, "mean": None}
    return {"count": len(values), "sum": sum(values), "mean": sum(values) / len(values)}
