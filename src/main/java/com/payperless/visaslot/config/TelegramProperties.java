package com.payperless.visaslot.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "telegram")
public record TelegramProperties(
    boolean enabled,
    String botToken,
    String chatId,
    @NotNull String apiBaseUrl,
    @NotNull Duration connectTimeout,
    @NotNull Duration readTimeout) {}
