package com.jd.genie.platform.usage;

import com.jd.genie.platform.usage.dto.UsageSummaryResponse;
import com.jd.genie.platform.usage.entity.ModelUsageRecordEntity;
import com.jd.genie.platform.usage.mapper.ModelUsageMapper;
import com.jd.genie.platform.usage.mapper.UsageDailyRow;
import com.jd.genie.platform.usage.mapper.UsageTotalsRow;
import com.jd.genie.platform.usage.mapper.UsageUserAggregateRow;
import com.jd.genie.platform.usage.service.UsageQueryService;
import com.jd.genie.platform.usage.service.UsageValidationException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UsageQueryServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-31T12:00:00Z"), ZoneOffset.UTC);

    private final CapturingMapper mapper = new CapturingMapper();
    private final UsageQueryService service = new UsageQueryService(mapper, CLOCK);

    @Test
    void missingBoundsDefaultToTheLastThirtyDaysEndingToday() {
        UsageSummaryResponse summary = service.tenantSummary("tenant-1", null, null);

        assertEquals("2026-03-02", summary.from());
        assertEquals("2026-03-31", summary.to());
        assertEquals(LocalDateTime.parse("2026-03-02T00:00"), mapper.lastFrom);
        assertEquals(LocalDateTime.parse("2026-04-01T00:00"), mapper.lastTo,
            "the upper bound is exclusive so the final day is fully included");
    }

    @Test
    void explicitBoundsArePassedThroughAsAHalfOpenInterval() {
        service.tenantSummary("tenant-1", "2026-03-10", "2026-03-12");

        assertEquals(LocalDateTime.parse("2026-03-10T00:00"), mapper.lastFrom);
        assertEquals(LocalDateTime.parse("2026-03-13T00:00"), mapper.lastTo);
    }

    @Test
    void invalidOrInvertedOrOversizedRangesAreRejected() {
        assertThrows(UsageValidationException.class,
            () -> service.tenantSummary("tenant-1", "not-a-date", null));
        assertThrows(UsageValidationException.class,
            () -> service.tenantSummary("tenant-1", "2026-03-12", "2026-03-10"));
        assertThrows(UsageValidationException.class,
            () -> service.tenantSummary("tenant-1", "2020-01-01", "2026-03-31"));
    }

    @Test
    void tokensAreReportedAsUnavailableUntilTheStreamingPathSuppliesThem() {
        assertFalse(service.tenantSummary("tenant-1", null, null).totals().tokensAvailable());

        mapper.totals.setTotalTokens(4_096);
        assertTrue(service.tenantSummary("tenant-1", null, null).totals().tokensAvailable());
    }

    @Test
    void userBreakdownRequestsOneExtraRowToDetectAFurtherPage() {
        mapper.aggregates = aggregates(21);

        var page = service.userBreakdown("tenant-1", null, null, 1, 20);

        assertEquals(20, page.items().size());
        assertTrue(page.hasMore());
        assertEquals(21, mapper.lastLimit);
        assertEquals(0, mapper.lastOffset);
    }

    @Test
    void invalidPaginationIsRejected() {
        assertThrows(UsageValidationException.class,
            () -> service.userBreakdown("tenant-1", null, null, 0, 20));
        assertThrows(UsageValidationException.class,
            () -> service.userBreakdown("tenant-1", null, null, 1, 101));
    }

    @Test
    void anEmptyAggregateRowStillProducesAUsableSummary() {
        mapper.totals = null;

        UsageSummaryResponse summary = service.tenantSummary("tenant-1", null, null);

        assertEquals(0, summary.totals().calls());
        assertTrue(summary.daily().isEmpty());
    }

    private static List<UsageUserAggregateRow> aggregates(int count) {
        List<UsageUserAggregateRow> rows = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            UsageUserAggregateRow row = new UsageUserAggregateRow();
            row.setUserId("user-" + index);
            row.setUsername("user" + index);
            row.setCalls(count - index);
            rows.add(row);
        }
        return rows;
    }

    private static final class CapturingMapper implements ModelUsageMapper {
        private UsageTotalsRow totals = new UsageTotalsRow();
        private List<UsageUserAggregateRow> aggregates = List.of();
        private LocalDateTime lastFrom;
        private LocalDateTime lastTo;
        private int lastOffset;
        private int lastLimit;

        @Override
        public int insertIgnore(ModelUsageRecordEntity record) {
            return 1;
        }

        @Override
        public UsageTotalsRow sumTenantTotals(String tenantId, LocalDateTime from, LocalDateTime to) {
            lastFrom = from;
            lastTo = to;
            return totals;
        }

        @Override
        public UsageTotalsRow sumUserTotals(String tenantId, String userId, LocalDateTime from, LocalDateTime to) {
            lastFrom = from;
            lastTo = to;
            return totals;
        }

        @Override
        public List<UsageDailyRow> listTenantDaily(String tenantId, LocalDateTime from, LocalDateTime to) {
            return List.of();
        }

        @Override
        public List<UsageDailyRow> listUserDaily(String tenantId, String userId, LocalDateTime from, LocalDateTime to) {
            return List.of();
        }

        @Override
        public List<UsageUserAggregateRow> listUserAggregates(String tenantId, LocalDateTime from, LocalDateTime to,
                                                             int offset, int limit) {
            lastOffset = offset;
            lastLimit = limit;
            return new ArrayList<>(aggregates);
        }
    }
}
