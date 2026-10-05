package com.example.coffeeshop.domain.menu.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.List;

@Getter
@AllArgsConstructor
public class PopularCoffeeMenuListResponse {
    private final List<PopularCoffeeMenuResponse> menus;
}
