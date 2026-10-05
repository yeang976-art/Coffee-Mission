package com.example.coffeeshop.domain.order.service;

import com.example.coffeeshop.common.exception.BusinessException;
import com.example.coffeeshop.common.exception.ErrorCode;
import com.example.coffeeshop.domain.history.entity.PointHistory;
import com.example.coffeeshop.domain.history.entity.PointHistoryType;
import com.example.coffeeshop.domain.history.repository.PointHistoryRepository;
import com.example.coffeeshop.domain.menu.entity.CoffeeMenu;
import com.example.coffeeshop.domain.menu.repository.CoffeeMenuRepository;
import com.example.coffeeshop.domain.order.dto.CoffeeOrderResponse;
import com.example.coffeeshop.domain.order.entity.CoffeeOrder;
import com.example.coffeeshop.domain.order.repository.CoffeeOrderRepository;
import com.example.coffeeshop.domain.outbox.entity.OrderOutbox;
import com.example.coffeeshop.domain.outbox.entity.OutboxStatus;
import com.example.coffeeshop.domain.outbox.repository.OrderOutboxRepository;
import com.example.coffeeshop.domain.user.entity.User;
import com.example.coffeeshop.domain.user.repository.UserRepository;
import com.example.coffeeshop.domain.wallet.entity.PointWallet;
import com.example.coffeeshop.domain.wallet.repository.PointWalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class CoffeeOrderService {
    private final UserRepository userRepository;
    private final CoffeeMenuRepository coffeeMenuRepository;
    private final PointWalletRepository pointWalletRepository;
    private final CoffeeOrderRepository coffeeOrderRepository;
    private final PointHistoryRepository pointHistoryRepository;
    private final OrderOutboxRepository orderOutboxRepository;
    private final Clock clock;

    @Transactional
    public CoffeeOrderResponse order(Long userId, Long menuId) {
        if (userId == null || userId <= 0 || menuId == null || menuId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        CoffeeMenu menu = coffeeMenuRepository.findById(menuId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MENU_NOT_FOUND));
        if (!Boolean.TRUE.equals(menu.getActive())) {
            throw new BusinessException(ErrorCode.MENU_NOT_AVAILABLE);
        }
        PointWallet wallet = pointWalletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));

        long paidPrice = menu.getPrice();
        wallet.spend(paidPrice);
        CoffeeOrder order = coffeeOrderRepository.save(new CoffeeOrder(user, menu, paidPrice, LocalDateTime.now(clock)));
        pointHistoryRepository.save(new PointHistory(wallet, order, PointHistoryType.SPEND, paidPrice, wallet.getBalance()));
        orderOutboxRepository.save(new OrderOutbox(order, OutboxStatus.PENDING));

        return new CoffeeOrderResponse(order.getId(), userId, menuId, paidPrice, wallet.getBalance(),
                order.getOrderedAt().toInstant(ZoneOffset.UTC));
    }
}
