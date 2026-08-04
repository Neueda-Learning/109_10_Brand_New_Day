package com.bnd.payment_processing.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * springdoc-openapi metadata (spec.md Section 9 - M4). Swagger UI is reachable
 * at /swagger-ui.html once the app is running (Section 13 - integration checklist).
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI paymentProcessingOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Payment Processing System API")
                        .description("Internal payment processing system: creation, validation, "
                                + "status transitions, audit trail, idempotency, and refunds.")
                        .version("v1"));
    }
}
