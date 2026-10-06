package com.example.coffeeshop.domain.outbox.dto;

public record OrderTransmission(Long eventId, Long orderId, Long userId, Long menuId, long paidPrice) {
}
