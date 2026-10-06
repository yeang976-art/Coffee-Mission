package com.example.coffeeshop;

import com.example.coffeeshop.common.exception.BusinessException;
import com.example.coffeeshop.common.exception.ErrorCode;
import com.example.coffeeshop.domain.history.entity.PointHistory;
import com.example.coffeeshop.domain.history.repository.PointHistoryRepository;
import com.example.coffeeshop.domain.menu.entity.CoffeeMenu;
import com.example.coffeeshop.domain.menu.repository.CoffeeMenuRepository;
import com.example.coffeeshop.domain.order.repository.CoffeeOrderRepository;
import com.example.coffeeshop.domain.order.service.CoffeeOrderService;
import com.example.coffeeshop.domain.outbox.repository.OrderOutboxRepository;
import com.example.coffeeshop.domain.user.entity.User;
import com.example.coffeeshop.domain.user.repository.UserRepository;
import com.example.coffeeshop.domain.wallet.entity.PointWallet;
import com.example.coffeeshop.domain.wallet.repository.PointWalletRepository;
import com.example.coffeeshop.domain.wallet.service.PointWalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

abstract class WalletConcurrencyScenarios {
    @Autowired UserRepository users;
    @Autowired CoffeeMenuRepository menus;
    @Autowired PointWalletRepository wallets;
    @Autowired PointHistoryRepository histories;
    @Autowired CoffeeOrderRepository orders;
    @Autowired OrderOutboxRepository outboxes;
    @Autowired CoffeeOrderService orderService;
    @Autowired PointWalletService pointService;
    @Autowired JdbcTemplate jdbc;
    protected User user;
    protected CoffeeMenu menu;

    @BeforeEach
    void prepare() {
        outboxes.deleteAllInBatch();
        histories.deleteAllInBatch();
        orders.deleteAllInBatch();
        wallets.deleteAllInBatch();
        menus.deleteAllInBatch();
        users.deleteAllInBatch();
        user = users.save(new User("concurrent@example.com"));
        wallets.save(new PointWallet(user, 10000L));
        menu = menus.save(new CoffeeMenu("아메리카노", 4500L));
    }

    @Test
    void 동시_주문은_잔액만큼만_성공하고_실패_주문은_기록을_남기지_않는다() throws Exception {
        verifyConcurrentOrders(List.of(orderService));
    }

    protected void verifyConcurrentOrders(List<CoffeeOrderService> services) throws Exception {
        var tasks = new ArrayList<Callable<Boolean>>();
        for (int i = 0; i < 20; i++) {
            CoffeeOrderService service = services.get(i % services.size());
            tasks.add(() -> {
                try {
                    service.order(user.getId(), menu.getId());
                    return true;
                } catch (BusinessException exception) {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_POINT);
                    return false;
                }
            });
        }
        List<Boolean> results = runTogether(tasks);
        assertThat(results.stream().filter(Boolean::booleanValue).count()).isEqualTo(2);
        assertThat(results.stream().filter(value -> !value).count()).isEqualTo(18);
        assertThat(wallets.findByUserId(user.getId()).orElseThrow().getBalance()).isEqualTo(1000);
        assertThat(orders.count()).isEqualTo(2);
        assertThat(histories.count()).isEqualTo(2);
        assertThat(outboxes.count()).isEqualTo(2);
        assertThat(histories.findAll()).extracting(PointHistory::getBalanceAfter)
                .containsExactlyInAnyOrder(5500L, 1000L);
    }

    @Test
    void 충전과_주문이_동시에_실행되어도_잔액_갱신이_누락되지_않는다() throws Exception {
        jdbc.update("update point_wallets set balance = 30000 where user_id = ?", user.getId());
        CoffeeMenu cheapMenu = menus.save(new CoffeeMenu("테스트 메뉴", 1000L));
        var tasks = new ArrayList<Callable<Boolean>>();
        for (int i = 0; i < 20; i++) {
            tasks.add(() -> {
                pointService.charge(user.getId(), 1000);
                return true;
            });
            tasks.add(() -> {
                orderService.order(user.getId(), cheapMenu.getId());
                return true;
            });
        }
        assertThat(runTogether(tasks)).containsOnly(true).hasSize(40);
        assertThat(wallets.findByUserId(user.getId()).orElseThrow().getBalance()).isEqualTo(30000);
        assertThat(orders.count()).isEqualTo(20);
        assertThat(histories.count()).isEqualTo(40);
        assertThat(outboxes.count()).isEqualTo(20);
        assertThat(histories.findAll()).allSatisfy(history -> assertThat(history.getBalanceAfter()).isNotNegative());
    }

    private List<Boolean> runTogether(List<Callable<Boolean>> tasks) throws Exception {
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(tasks.size())) {
            var futures = new ArrayList<java.util.concurrent.Future<Boolean>>();
            for (Callable<Boolean> task : tasks) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("동시 요청 시작 대기 시간이 초과되었습니다.");
                    }
                    return task.call();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var results = new ArrayList<Boolean>();
            for (var future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        }
    }
}
