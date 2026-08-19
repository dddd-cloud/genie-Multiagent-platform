package com.jd.genie.platform.usage.service;

import com.jd.genie.platform.contract.PageResponse;
import com.jd.genie.platform.usage.dto.UsageDailyPoint;
import com.jd.genie.platform.usage.dto.UsageSummaryResponse;
import com.jd.genie.platform.usage.dto.UsageTotals;
import com.jd.genie.platform.usage.dto.UsageUserRow;
import com.jd.genie.platform.usage.mapper.ModelUsageMapper;
import com.jd.genie.platform.usage.mapper.UsageDailyRow;
import com.jd.genie.platform.usage.mapper.UsageTotalsRow;
import com.jd.genie.platform.usage.mapper.UsageUserAggregateRow;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

@Service
public class UsageQueryService {

    private static final int DEFAULT_RANGE_DAYS = 30;
    private static final int MAX_RANGE_DAYS = 366;
    private static final int MAX_PAGE_SIZE = 100;

    private final ModelUsageMapper modelUsageMapper;
    private final Clock clock;

    public UsageQueryService(ModelUsageMapper modelUsageMapper, Clock clock) {
        this.modelUsageMapper = modelUsageMapper;
        this.clock = clock;
    }

    /** Inclusive day range resolved against the server clock; both bounds may be null. */
    public record DateRange(LocalDate from, LocalDate to) {
        LocalDateTime fromInclusive() {
            return from.atStartOfDay();
        }

        LocalDateTime toExclusive() {
            return to.plusDays(1).atStartOfDay();
        }
    }

    public UsageSummaryResponse tenantSummary(String tenantId, String from, String to) {
        DateRange range = resolveRange(from, to);
        UsageTotalsRow totals = modelUsageMapper.sumTenantTotals(tenantId, range.fromInclusive(), range.toExclusive());
        List<UsageDailyRow> daily = modelUsageMapper.listTenantDaily(tenantId, range.fromInclusive(), range.toExclusive());
        return summary(range, totals, daily);
    }

    public UsageSummaryResponse userSummary(String tenantId, String userId, String from, String to) {
        DateRange range = resolveRange(from, to);
        UsageTotalsRow totals = modelUsageMapper.sumUserTotals(tenantId, userId, range.fromInclusive(), range.toExclusive());
        List<UsageDailyRow> daily = modelUsageMapper.listUserDaily(tenantId, userId, range.fromInclusive(), range.toExclusive());
        return summary(range, totals, daily);
    }

    public PageResponse<UsageUserRow> userBreakdown(String tenantId, String from, String to, int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new UsageValidationException("invalid page");
        }
        DateRange range = resolveRange(from, to);
        List<UsageUserAggregateRow> rows = modelUsageMapper.listUserAggregates(
            tenantId, range.fromInclusive(), range.toExclusive(), (page - 1) * pageSize, pageSize + 1);
        boolean hasMore = rows.size() > pageSize;
        if (hasMore) {
            rows = rows.subList(0, pageSize);
        }
        List<UsageUserRow> items = rows.stream()
            .map(row -> new UsageUserRow(row.getUserId(), row.getUsername(), row.getDisplayName(), row.getCalls(),
                row.getCompletedCalls(), row.getFailedCalls(), row.getTotalDurationMs(), row.getTotalTokens()))
            .toList();
        return new PageResponse<>(items, page, pageSize, hasMore);
    }

    private UsageSummaryResponse summary(DateRange range, UsageTotalsRow totals, List<UsageDailyRow> daily) {
        UsageTotalsRow safeTotals = totals == null ? new UsageTotalsRow() : totals;
        List<UsageDailyPoint> points = daily.stream()
            .map(row -> new UsageDailyPoint(row.getDay(), row.getCalls(), row.getCompletedCalls(), row.getFailedCalls(),
                row.getTotalTokens()))
            .toList();
        return new UsageSummaryResponse(range.from().toString(), range.to().toString(), new UsageTotals(
            safeTotals.getCalls(), safeTotals.getCompletedCalls(), safeTotals.getFailedCalls(),
            safeTotals.getInterruptedCalls(), safeTotals.getTotalDurationMs(), safeTotals.getPromptTokens(),
            safeTotals.getCompletionTokens(), safeTotals.getTotalTokens(), safeTotals.getTotalTokens() > 0), points);
    }

    private DateRange resolveRange(String from, String to) {
        LocalDate today = LocalDate.now(clock);
        LocalDate resolvedTo = parseDate(to, today);
        LocalDate resolvedFrom = parseDate(from, resolvedTo.minusDays(DEFAULT_RANGE_DAYS - 1L));
        if (resolvedFrom.isAfter(resolvedTo)) {
            throw new UsageValidationException("from must not be after to");
        }
        if (resolvedFrom.plusDays(MAX_RANGE_DAYS).isBefore(resolvedTo)) {
            throw new UsageValidationException("range must not exceed " + MAX_RANGE_DAYS + " days");
        }
        return new DateRange(resolvedFrom, resolvedTo);
    }

    private static LocalDate parseDate(String raw, LocalDate fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException ex) {
            throw new UsageValidationException("invalid date: " + raw);
        }
    }
}
