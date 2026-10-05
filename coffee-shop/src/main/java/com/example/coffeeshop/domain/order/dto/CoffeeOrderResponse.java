package com.example.coffeeshop.domain.order.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.time.Instant;

@Getter
@AllArgsConstructor
public class CoffeeOrderResponse {
    private final Long orderId;
    private final Long userId;
    private final Long menuId;
    private final Long paidPrice;
    private final Long remainingBalance;
    private final Instant orderedAt;
}
