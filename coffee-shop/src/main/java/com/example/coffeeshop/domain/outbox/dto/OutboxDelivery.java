package com.example.coffeeshop.domain.outbox.dto;

public record OutboxDelivery(OrderTransmission payload, int attemptCount) {
}
