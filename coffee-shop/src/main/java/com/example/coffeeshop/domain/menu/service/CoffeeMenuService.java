package com.example.coffeeshop.domain.menu.service;

import com.example.coffeeshop.domain.menu.dto.CoffeeMenuListResponse;
import com.example.coffeeshop.domain.menu.dto.CoffeeMenuResponse;
import com.example.coffeeshop.domain.menu.dto.PopularCoffeeMenuResponse;
import com.example.coffeeshop.domain.menu.dto.PopularCoffeeMenuListResponse;
import com.example.coffeeshop.domain.order.repository.CoffeeOrderRepository;
import org.springframework.data.domain.PageRequest;
import java.time.Clock;
import java.time.LocalDateTime;
import com.example.coffeeshop.domain.menu.repository.CoffeeMenuRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CoffeeMenuService {
    private final CoffeeMenuRepository coffeeMenuRepository;
    private final CoffeeOrderRepository coffeeOrderRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public CoffeeMenuListResponse getMenus() {
        List<CoffeeMenuResponse> menus = coffeeMenuRepository.findByActiveTrueOrderByIdAsc().stream()
                .map(menu -> new CoffeeMenuResponse(menu.getId(), menu.getName(), menu.getPrice()))
                .toList();
        return new CoffeeMenuListResponse(menus);
    }

    @Transactional(readOnly = true)
    public PopularCoffeeMenuListResponse getPopularMenus() {
        LocalDateTime until = LocalDateTime.now(clock);
        List<PopularCoffeeMenuResponse> menus = coffeeOrderRepository
                .findPopularMenus(until.minusDays(7), until, PageRequest.of(0, 3)).stream()
                .map(menu -> new PopularCoffeeMenuResponse(menu.getMenuId(), menu.getName(), menu.getPrice(),
                        menu.getActive(), menu.getOrderCount()))
                .toList();
        return new PopularCoffeeMenuListResponse(menus);
    }
}
