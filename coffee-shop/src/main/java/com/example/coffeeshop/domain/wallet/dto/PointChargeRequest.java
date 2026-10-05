package com.example.coffeeshop.domain.wallet.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PointChargeRequest {
    @NotNull
    @Positive
    private Long userId;

    @NotNull
    private Long amount;
}
