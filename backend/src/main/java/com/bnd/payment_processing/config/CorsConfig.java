package com.bnd.payment_processing.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Allows the plain-HTML dev frontend (served from a local static file server,
 * e.g. http://localhost:5500) to call the API from the browser - see spec.md
 * Section 10 "Global API policy" and Section 11.3.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(
                        "http://localhost:5500",
                        "http://127.0.0.1:5500",
                        "http://localhost:3000"
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
