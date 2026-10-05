package com.example.coffeeshop.domain.menu.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CoffeeMenuResponse {
    private final Long menuId;
    private final String name;
    private final Long price;
}
