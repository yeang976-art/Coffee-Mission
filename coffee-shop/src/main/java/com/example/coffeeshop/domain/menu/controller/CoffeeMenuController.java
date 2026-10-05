package com.example.coffeeshop.domain.menu.controller;

import com.example.coffeeshop.common.response.ApiResponse;
import com.example.coffeeshop.domain.menu.dto.CoffeeMenuListResponse;
import com.example.coffeeshop.domain.menu.dto.PopularCoffeeMenuListResponse;
import com.example.coffeeshop.domain.menu.service.CoffeeMenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/coffee-menus")
@RequiredArgsConstructor
public class CoffeeMenuController {
    private final CoffeeMenuService coffeeMenuService;

    @GetMapping
    public ApiResponse<CoffeeMenuListResponse> getMenus() {
        return ApiResponse.of("커피 메뉴를 조회했습니다.", coffeeMenuService.getMenus());
    }

    @GetMapping("/popular")
    public ApiResponse<PopularCoffeeMenuListResponse> getPopularMenus() {
        return ApiResponse.of("인기 메뉴를 조회했습니다.", coffeeMenuService.getPopularMenus());
    }
}
