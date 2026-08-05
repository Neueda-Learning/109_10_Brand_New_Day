package com.bnd.payment_processing.payment.repository;

import com.bnd.payment_processing.payment.dto.PaymentInsightsResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository-level tests for {@link JdbcPaymentAnalyticsRepository} against the real,
 * locally-running MySQL instance (spec.md Section 15 - requires `docker compose up -d`).
 * Assertions cross-check against {@link PaymentRepository#countSearch} rather than
 * hardcoding the seeded dataset's absolute counts, so they stay valid if data.sql is
 * regenerated. Each test runs inside a transaction that is rolled back afterward.
 */
@SpringBootTest
@Transactional
class JdbcPaymentAnalyticsRepositoryTest {

    @Autowired
    private PaymentAnalyticsRepository paymentAnalyticsRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void getInsights_noFilters_totalCountMatchesUnfilteredSearchCount() {
        PaymentInsightsResponse insights = paymentAnalyticsRepository.getInsights(new LinkedHashMap<>());

        long expectedTotal = paymentRepository.countSearch(new LinkedHashMap<>());
        assertThat(insights.getTotalCount()).isEqualTo(expectedTotal);
        assertThat(insights.getTotalAmount()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    void getInsights_noFilters_countByStatusSumsToTotalCount() {
        PaymentInsightsResponse insights = paymentAnalyticsRepository.getInsights(new LinkedHashMap<>());

        long sumByStatus = insights.getCountByStatus().values().stream().mapToLong(Long::longValue).sum();
        assertThat(sumByStatus).isEqualTo(insights.getTotalCount());
    }

    @Test
    void getInsights_noFilters_countByTypeAndAmountByTypeSumToTotals() {
        PaymentInsightsResponse insights = paymentAnalyticsRepository.getInsights(new LinkedHashMap<>());

        long sumByType = insights.getCountByType().values().stream().mapToLong(Long::longValue).sum();
        assertThat(sumByType).isEqualTo(insights.getTotalCount());

        BigDecimal sumAmountByType = insights.getAmountByType().values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sumAmountByType).isEqualByComparingTo(insights.getTotalAmount());
    }

    @Test
    void getInsights_noFilters_dailyVolumeSumsToTotalCountAndIsOrderedByDate() {
        PaymentInsightsResponse insights = paymentAnalyticsRepository.getInsights(new LinkedHashMap<>());

        long sumDaily = insights.getDailyVolume().stream()
                .mapToLong(PaymentInsightsResponse.DailyVolumeEntry::getCount)
                .sum();
        assertThat(sumDaily).isEqualTo(insights.getTotalCount());

        for (int i = 1; i < insights.getDailyVolume().size(); i++) {
            assertThat(insights.getDailyVolume().get(i).getDate())
                    .isAfter(insights.getDailyVolume().get(i - 1).getDate());
        }
    }

    @Test
    void getInsights_noFilters_successRateBetweenZeroAndOneInclusive() {
        PaymentInsightsResponse insights = paymentAnalyticsRepository.getInsights(new LinkedHashMap<>());

        assertThat(insights.getSuccessRate()).isNotNull();
        assertThat(insights.getSuccessRate()).isBetween(0.0, 1.0);
    }

    @Test
    void getInsights_filteredByStatusCompleted_countByStatusOnlyHasCompletedRows() {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("status", "COMPLETED");

        PaymentInsightsResponse insights = paymentAnalyticsRepository.getInsights(filters);

        assertThat(insights.getTotalCount()).isEqualTo(insights.getCountByStatus().get("COMPLETED"));
        insights.getCountByStatus().forEach((status, count) -> {
            if (!"COMPLETED".equals(status)) {
                assertThat(count).isZero();
            }
        });
    }

    @Test
    void getInsights_pendingApprovalCount_isZeroUntilM3SchemaLands() {
        PaymentInsightsResponse insights = paymentAnalyticsRepository.getInsights(new LinkedHashMap<>());

        assertThat(insights.getPendingApprovalCount()).isZero();
    }

    @Test
    void getInsights_futureFromDate_returnsZeroCounts() {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("fromDate", LocalDate.now().plusYears(10));

        PaymentInsightsResponse insights = paymentAnalyticsRepository.getInsights(filters);

        assertThat(insights.getTotalCount()).isZero();
        assertThat(insights.getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(insights.getSuccessRate()).isNull();
        assertThat(insights.getRefundRate()).isNull();
        assertThat(insights.getDailyVolume()).isEmpty();
    }
}
