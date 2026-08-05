package com.bnd.payment_processing.bootstrap.controller;

import com.bnd.payment_processing.bootstrap.dto.BootstrapResponse;
import com.bnd.payment_processing.bootstrap.service.BootstrapService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/bootstrap} - checkout bootstrap data (spec.md Section 7.1).
 */
@RestController
public class BootstrapController {

    private final BootstrapService bootstrapService;

    public BootstrapController(BootstrapService bootstrapService) {
        this.bootstrapService = bootstrapService;
    }

    @GetMapping("/api/bootstrap")
    public BootstrapResponse getBootstrap() {
        return bootstrapService.getBootstrap();
    }
}
