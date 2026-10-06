package com.example.coffeeshop.domain.outbox.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "outbox")
public record OutboxProperties(@NotBlank String url, @Min(1) int batchSize,
                               @Min(1) long retryDelaySeconds, @Min(10) long leaseSeconds,
                               @Min(1) int requestTimeoutMillis) {
}
