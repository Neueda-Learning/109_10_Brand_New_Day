package com.bnd.payment_processing.payment.controller;

import com.bnd.payment_processing.payment.dto.PaymentInsightsResponse;
import com.bnd.payment_processing.payment.service.PaymentAnalyticsService;
import com.bnd.payment_processing.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves {@code GET /api/payments/insights} (spec.md Section 10.10) routes to
 * {@link PaymentQueryController#getInsights}, not {@link PaymentController#getPayment}'s
 * {@code /{id}} path-variable lookup - the routing collision risk called out in
 * spec.md Section 10.10/15. Both controllers are loaded together since that's the only
 * way a real misrouting would surface.
 */
@WebMvcTest({PaymentController.class, PaymentQueryController.class})
class PaymentQueryControllerRoutingTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private PaymentAnalyticsService paymentAnalyticsService;

    @Test
    void getInsights_routesToAnalyticsEndpoint_notToPaymentByIdLookup() throws Exception {
        PaymentInsightsResponse stub = new PaymentInsightsResponse();
        stub.setTotalCount(491);
        when(paymentAnalyticsService.getInsights(isNull(), isNull(), isNull(), isNull())).thenReturn(stub);

        mockMvc.perform(get("/api/payments/insights"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(491));

        verify(paymentService, never()).getPayment(any());
    }
}
