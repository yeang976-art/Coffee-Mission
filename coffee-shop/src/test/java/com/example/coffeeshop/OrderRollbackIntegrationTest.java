package com.example.coffeeshop;

import com.example.coffeeshop.domain.history.repository.PointHistoryRepository;
import com.example.coffeeshop.domain.menu.entity.CoffeeMenu;
import com.example.coffeeshop.domain.menu.repository.CoffeeMenuRepository;
import com.example.coffeeshop.domain.order.repository.CoffeeOrderRepository;
import com.example.coffeeshop.domain.order.service.CoffeeOrderService;
import com.example.coffeeshop.domain.outbox.entity.OrderOutbox;
import com.example.coffeeshop.domain.outbox.repository.OrderOutboxRepository;
import com.example.coffeeshop.domain.user.entity.User;
import com.example.coffeeshop.domain.user.repository.UserRepository;
import com.example.coffeeshop.domain.wallet.entity.PointWallet;
import com.example.coffeeshop.domain.wallet.repository.PointWalletRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class OrderRollbackIntegrationTest {
    @Autowired UserRepository users;
    @Autowired CoffeeMenuRepository menus;
    @Autowired PointWalletRepository wallets;
    @Autowired PointHistoryRepository histories;
    @Autowired CoffeeOrderRepository orders;
    @Autowired CoffeeOrderService orderService;
    @MockitoBean OrderOutboxRepository outboxes;

    @Test
    void Outbox_저장에_실패하면_차감과_주문과_이력을_롤백한다() {
        User user = users.save(new User("rollback@example.com"));
        wallets.save(new PointWallet(user, 10000L));
        CoffeeMenu menu = menus.save(new CoffeeMenu("아메리카노", 4500L));
        when(outboxes.save(any(OrderOutbox.class))).thenThrow(new IllegalStateException("Outbox 저장 실패"));

        assertThatThrownBy(() -> orderService.order(user.getId(), menu.getId()))
                .isInstanceOf(IllegalStateException.class).hasMessage("Outbox 저장 실패");
        assertThat(wallets.findByUserId(user.getId()).orElseThrow().getBalance()).isEqualTo(10000);
        assertThat(orders.count()).isZero();
        assertThat(histories.count()).isZero();
    }
}
