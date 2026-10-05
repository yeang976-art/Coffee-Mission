package com.example.coffeeshop.domain.menu.service;

import com.example.coffeeshop.domain.menu.dto.CoffeeMenuListResponse;
import com.example.coffeeshop.domain.menu.dto.CoffeeMenuResponse;
import com.example.coffeeshop.domain.menu.repository.CoffeeMenuRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CoffeeMenuService {
    private final CoffeeMenuRepository coffeeMenuRepository;

    @Transactional(readOnly = true)
    public CoffeeMenuListResponse getMenus() {
        List<CoffeeMenuResponse> menus = coffeeMenuRepository.findByActiveTrueOrderByIdAsc().stream()
                .map(menu -> new CoffeeMenuResponse(menu.getId(), menu.getName(), menu.getPrice()))
                .toList();
        return new CoffeeMenuListResponse(menus);
    }
}
