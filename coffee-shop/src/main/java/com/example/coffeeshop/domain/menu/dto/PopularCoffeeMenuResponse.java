package com.example.coffeeshop.domain.menu.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PopularCoffeeMenuResponse {
    private final Long menuId;
    private final String name;
    private final Long price;
    private final Boolean active;
    private final Long orderCount;
}
