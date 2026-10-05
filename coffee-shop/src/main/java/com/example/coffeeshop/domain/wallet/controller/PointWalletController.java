package com.example.coffeeshop.domain.wallet.controller;

import com.example.coffeeshop.common.response.ApiResponse;
import com.example.coffeeshop.domain.wallet.dto.PointBalanceResponse;
import com.example.coffeeshop.domain.wallet.dto.PointChargeRequest;
import com.example.coffeeshop.domain.wallet.dto.PointChargeResponse;
import com.example.coffeeshop.domain.wallet.service.PointWalletService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/points")
@RequiredArgsConstructor
public class PointWalletController {
    private final PointWalletService pointWalletService;

    @GetMapping
    public ApiResponse<PointBalanceResponse> getBalance(@RequestParam("userId") @Positive Long userId) {
        return ApiResponse.of("포인트를 조회했습니다.", pointWalletService.getBalance(userId));
    }

    @PostMapping("/charge")
    public ApiResponse<PointChargeResponse> charge(@Valid @RequestBody PointChargeRequest request) {
        return ApiResponse.of("포인트를 충전했습니다.", pointWalletService.charge(request.getUserId(), request.getAmount()));
    }
}
