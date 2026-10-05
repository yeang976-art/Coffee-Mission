package com.example.coffeeshop.domain.wallet.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PointChargeResponse {
    private final Long userId;
    private final Long chargedAmount;
    private final Long balance;
}
