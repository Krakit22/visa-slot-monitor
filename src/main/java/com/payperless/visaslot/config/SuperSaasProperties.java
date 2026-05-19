package com.payperless.visaslot.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "supersaas")
public record SuperSaasProperties(
    @NotBlank String baseUrl,
    @NotBlank String schedulePath,
    @NotBlank String bookingUrl,
    @NotNull Duration metadataTtl,
    @NotBlank String userAgent,
    @NotNull Duration connectTimeout,
    @NotNull Duration readTimeout) {}
