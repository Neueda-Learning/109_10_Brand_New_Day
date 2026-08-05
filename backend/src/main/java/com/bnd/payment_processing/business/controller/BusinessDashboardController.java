package com.bnd.payment_processing.business.controller;

import com.bnd.payment_processing.business.dto.BusinessDashboardResponse;
import com.bnd.payment_processing.business.service.BusinessDashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/business/dashboard} - business KPI aggregates (spec.md Section 7.8).
 */
@RestController
public class BusinessDashboardController {

    private final BusinessDashboardService businessDashboardService;

    public BusinessDashboardController(BusinessDashboardService businessDashboardService) {
        this.businessDashboardService = businessDashboardService;
    }

    @GetMapping("/api/business/dashboard")
    public BusinessDashboardResponse getDashboard() {
        return businessDashboardService.getDashboard();
    }
}
