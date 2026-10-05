package com.example.coffeeshop.domain.wallet.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PointBalanceResponse {
    private final Long userId;
    private final Long balance;
}
