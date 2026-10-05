package com.example.coffeeshop.domain.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CoffeeOrderRequest {
    @NotNull
    @Positive
    private Long userId;

    @NotNull
    @Positive
    private Long menuId;
}
