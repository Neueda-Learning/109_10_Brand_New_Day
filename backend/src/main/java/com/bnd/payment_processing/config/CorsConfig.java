package com.bnd.payment_processing.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Allows the plain-HTML dev frontend (served from a local static file server)
 * to call the API from the browser - see spec.md Section 10 "Global API policy"
 * and Section 11.3.
 *
 * NOTE: allowedOriginPatterns("*") is a dev-only convenience so any local
 * static server port works without editing this file each time. Tighten this
 * to an explicit allowedOrigins(...) list before any real deployment.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false);
    }
}