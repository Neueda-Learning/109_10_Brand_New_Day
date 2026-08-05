package com.bnd.payment_processing.payment.service;

import com.bnd.payment_processing.payment.dto.PaymentInsightsResponse;
import com.bnd.payment_processing.payment.repository.PaymentAnalyticsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PaymentAnalyticsServiceImpl} (spec.md Section 10.10).
 * {@link PaymentAnalyticsRepository} is mocked - no database needed.
 */
@ExtendWith(MockitoExtension.class)
class PaymentAnalyticsServiceImplTest {

    @Mock
    private PaymentAnalyticsRepository paymentAnalyticsRepository;

    private PaymentAnalyticsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PaymentAnalyticsServiceImpl(paymentAnalyticsRepository);
    }

    @Test
    void getInsights_noFilters_passesEmptyFilterMapToRepository() {
        PaymentInsightsResponse stub = new PaymentInsightsResponse();
        when(paymentAnalyticsRepository.getInsights(any())).thenReturn(stub);

        PaymentInsightsResponse result = service.getInsights(null, null, null, null);

        assertThat(result).isSameAs(stub);
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(paymentAnalyticsRepository).getInsights(captor.capture());
        assertThat(captor.getValue()).isEmpty();
    }

    @Test
    void getInsights_validStatusAndType_uppercasesAndPassesThroughAllFilters() {
        when(paymentAnalyticsRepository.getInsights(any())).thenReturn(new PaymentInsightsResponse());
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 5);

        service.getInsights("completed", "payment", from, to);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(paymentAnalyticsRepository).getInsights(captor.capture());
        Map<String, Object> filters = captor.getValue();
        assertThat(filters.get("status")).isEqualTo("COMPLETED");
        assertThat(filters.get("type")).isEqualTo("PAYMENT");
        assertThat(filters.get("fromDate")).isEqualTo(from);
        assertThat(filters.get("toDate")).isEqualTo(to);
    }

    @Test
    void getInsights_invalidStatus_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> service.getInsights("not-a-status", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("status");
    }

    @Test
    void getInsights_invalidType_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> service.getInsights(null, "not-a-type", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("type");
    }
}
