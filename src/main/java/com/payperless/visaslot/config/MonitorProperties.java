package com.payperless.visaslot.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "monitor")
public record MonitorProperties(
    @NotNull Duration checkInterval,
    @NotNull Duration lookahead,
    @NotNull ZoneId scheduleZone,
    @NotNull Duration dedupReset) {}
