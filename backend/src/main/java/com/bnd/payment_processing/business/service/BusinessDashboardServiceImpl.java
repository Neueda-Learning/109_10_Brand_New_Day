package com.bnd.payment_processing.business.service;

import com.bnd.payment_processing.business.dto.BusinessDashboardResponse;
import com.bnd.payment_processing.business.repository.BusinessDashboardRepository;
import com.bnd.payment_processing.payment.dto.PaymentMapper;
import com.bnd.payment_processing.payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;

@Service
public class BusinessDashboardServiceImpl implements BusinessDashboardService {

    private static final int RECENT_PAYMENTS_LIMIT = 10;

    private final BusinessDashboardRepository dashboardRepository;
    private final PaymentRepository paymentRepository;

    public BusinessDashboardServiceImpl(BusinessDashboardRepository dashboardRepository,
                                        PaymentRepository paymentRepository) {
        this.dashboardRepository = dashboardRepository;
        this.paymentRepository = paymentRepository;
    }

    @Override
    public BusinessDashboardResponse getDashboard() {
        var recentPayments = paymentRepository.search(Collections.emptyMap(), 0, RECENT_PAYMENTS_LIMIT).stream()
                .map(PaymentMapper::toResponse)
                .toList();

        return new BusinessDashboardResponse(
                dashboardRepository.sumCompletedUsdAmount(),
                dashboardRepository.sumGstCollected(),
                dashboardRepository.countInvoices(),
                dashboardRepository.countPaymentsByStatus(),
                dashboardRepository.countPendingSettlements(),
                dashboardRepository.countPendingRefundApprovals(),
                recentPayments);
    }
}
