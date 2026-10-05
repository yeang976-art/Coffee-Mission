package com.example.coffeeshop.domain.order.controller;

import com.example.coffeeshop.common.response.ApiResponse;
import com.example.coffeeshop.domain.order.dto.CoffeeOrderRequest;
import com.example.coffeeshop.domain.order.dto.CoffeeOrderResponse;
import com.example.coffeeshop.domain.order.service.CoffeeOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class CoffeeOrderController {
    private final CoffeeOrderService coffeeOrderService;

    @PostMapping
    public ResponseEntity<ApiResponse<CoffeeOrderResponse>> order(@Valid @RequestBody CoffeeOrderRequest request) {
        CoffeeOrderResponse response = coffeeOrderService.order(request.getUserId(), request.getMenuId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of("주문과 결제가 완료되었습니다.", response));
    }
}
