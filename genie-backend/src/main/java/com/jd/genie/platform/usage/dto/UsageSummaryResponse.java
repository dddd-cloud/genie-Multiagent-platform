package com.jd.genie.platform.usage.dto;

import java.util.List;

public record UsageSummaryResponse(
    String from,
    String to,
    UsageTotals totals,
    List<UsageDailyPoint> daily
) {
}
