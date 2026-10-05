package com.example.coffeeshop;

import com.example.coffeeshop.common.exception.BusinessException;
import com.example.coffeeshop.common.exception.ErrorCode;
import com.example.coffeeshop.domain.history.entity.PointHistory;
import com.example.coffeeshop.domain.history.entity.PointHistoryType;
import com.example.coffeeshop.domain.history.repository.PointHistoryRepository;
import com.example.coffeeshop.domain.menu.dto.PopularCoffeeMenuResponse;
import com.example.coffeeshop.domain.menu.entity.CoffeeMenu;
import com.example.coffeeshop.domain.menu.repository.CoffeeMenuRepository;
import com.example.coffeeshop.domain.menu.service.CoffeeMenuService;
import com.example.coffeeshop.domain.order.dto.CoffeeOrderResponse;
import com.example.coffeeshop.domain.order.entity.CoffeeOrder;
import com.example.coffeeshop.domain.order.repository.CoffeeOrderRepository;
import com.example.coffeeshop.domain.order.service.CoffeeOrderService;
import com.example.coffeeshop.domain.outbox.entity.OrderOutbox;
import com.example.coffeeshop.domain.outbox.entity.OutboxStatus;
import com.example.coffeeshop.domain.outbox.repository.OrderOutboxRepository;
import com.example.coffeeshop.domain.user.entity.User;
import com.example.coffeeshop.domain.user.repository.UserRepository;
import com.example.coffeeshop.domain.wallet.entity.PointWallet;
import com.example.coffeeshop.domain.wallet.repository.PointWalletRepository;
import com.example.coffeeshop.domain.wallet.service.PointWalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class CoffeeWorkflowIntegrationTest {
    @Autowired UserRepository users;
    @Autowired CoffeeMenuRepository menus;
    @Autowired PointWalletRepository wallets;
    @Autowired PointHistoryRepository histories;
    @Autowired CoffeeOrderRepository orders;
    @Autowired OrderOutboxRepository outboxes;
    @Autowired CoffeeMenuService menuService;
    @Autowired PointWalletService pointService;
    @Autowired CoffeeOrderService orderService;
    @Autowired JdbcTemplate jdbc;
    @Autowired WebApplicationContext context;
    @Autowired Clock clock;
    private MockMvc mvc;
    private User user;
    private CoffeeMenu menu;

    @TestConfiguration
    static class FixedTimeConfig {
        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(Instant.parse("2026-10-05T10:00:00Z"), ZoneOffset.UTC);
        }
    }

    @BeforeEach
    void prepare() {
        outboxes.deleteAllInBatch();
        histories.deleteAllInBatch();
        orders.deleteAllInBatch();
        wallets.deleteAllInBatch();
        menus.deleteAllInBatch();
        users.deleteAllInBatch();
        user = users.save(new User("coffee@example.com"));
        wallets.save(new PointWallet(user, 10000L));
        menu = menus.save(new CoffeeMenu("아메리카노", 4500L));
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void 충전과_충전_이력이_함께_저장된다() {
        assertThat(pointService.charge(user.getId(), 2000).getBalance()).isEqualTo(12000);
        PointHistory history = histories.findAll().getFirst();
        assertThat(history.getType()).isEqualTo(PointHistoryType.CHARGE);
        assertThat(history.getAmount()).isEqualTo(2000);
        assertThat(history.getBalanceAfter()).isEqualTo(12000);
        assertThat(history.getCoffeeOrder()).isNull();
    }

    @Test
    void 주문과_차감과_이력과_Outbox가_함께_저장된다() {
        CoffeeOrderResponse response = orderService.order(user.getId(), menu.getId());
        assertThat(response.getPaidPrice()).isEqualTo(4500);
        assertThat(response.getRemainingBalance()).isEqualTo(5500);
        assertThat(response.getOrderedAt()).isEqualTo(clock.instant());
        assertThat(wallets.findByUserId(user.getId()).orElseThrow().getBalance()).isEqualTo(5500);
        PointHistory history = histories.findAll().getFirst();
        assertThat(history.getType()).isEqualTo(PointHistoryType.SPEND);
        assertThat(history.getCoffeeOrder().getId()).isEqualTo(response.getOrderId());
        assertThat(history.getAmount()).isEqualTo(4500);
        assertThat(history.getBalanceAfter()).isEqualTo(5500);
        assertThat(outboxes.count()).isEqualTo(1);
        assertThat(outboxes.findAll().getFirst().getStatus()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    void 잔액_부족_주문은_아무_기록도_남기지_않는다() {
        jdbc.update("update point_wallets set balance = 1000 where user_id = ?", user.getId());
        assertThatThrownBy(() -> orderService.order(user.getId(), menu.getId()))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(ErrorCode.INSUFFICIENT_POINT);
        assertThat(wallets.findByUserId(user.getId()).orElseThrow().getBalance()).isEqualTo(1000);
        assertThat(orders.count()).isZero();
        assertThat(histories.count()).isZero();
        assertThat(outboxes.count()).isZero();
    }

    @Test
    void 판매_중지_메뉴는_주문할_수_없다() {
        jdbc.update("update coffee_menus set active = false where id = ?", menu.getId());
        assertThatThrownBy(() -> orderService.order(user.getId(), menu.getId()))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(ErrorCode.MENU_NOT_AVAILABLE);
        assertThat(wallets.findByUserId(user.getId()).orElseThrow().getBalance()).isEqualTo(10000);
        assertThat(orders.count()).isZero();
    }

    @Test
    void 메뉴_가격이_변경되어도_결제_금액은_유지된다() {
        Long orderId = orderService.order(user.getId(), menu.getId()).getOrderId();
        jdbc.update("update coffee_menus set price = 6000 where id = ?", menu.getId());
        assertThat(orders.findById(orderId).orElseThrow().getPaidPrice()).isEqualTo(4500);
    }

    @Test
    void 인기_메뉴는_최근_7일_주문을_횟수와_ID_순서로_최대_3개_반환한다() {
        CoffeeMenu second = menus.save(new CoffeeMenu("라떼", 5000L));
        CoffeeMenu third = menus.save(new CoffeeMenu("모카", 5500L));
        CoffeeMenu fourth = menus.save(new CoffeeMenu("콜드브루", 6000L));
        LocalDateTime until = LocalDateTime.now(clock);
        orders.save(new CoffeeOrder(user, menu, 4500L, until.minusDays(7)));
        orders.save(new CoffeeOrder(user, menu, 4500L, until.minusDays(1)));
        orders.save(new CoffeeOrder(user, second, 5000L, until.minusDays(1)));
        orders.save(new CoffeeOrder(user, second, 5000L, until.minusDays(2)));
        orders.save(new CoffeeOrder(user, third, 5500L, until.minusHours(1)));
        orders.save(new CoffeeOrder(user, fourth, 6000L, until.minusHours(1)));
        for (int i = 0; i < 5; i++) {
            orders.save(new CoffeeOrder(user, fourth, 6000L, until.minusDays(7).minusSeconds(1)));
        }
        orders.save(new CoffeeOrder(user, fourth, 6000L, until.plusSeconds(1)));
        jdbc.update("update coffee_menus set active = false where id = ?", second.getId());
        var result = menuService.getPopularMenus().getMenus();
        assertThat(result).extracting(PopularCoffeeMenuResponse::getMenuId)
                .containsExactly(menu.getId(), second.getId(), third.getId());
        assertThat(result).extracting(PopularCoffeeMenuResponse::getOrderCount).containsExactly(2L, 2L, 1L);
        assertThat(result.get(1).getActive()).isFalse();
    }

    @Test
    void 주문이_없으면_인기_메뉴는_빈_배열이다() {
        assertThat(menuService.getPopularMenus().getMenus()).isEmpty();
    }

    @Test
    void 사용자당_지갑은_하나만_허용한다() {
        assertThatThrownBy(() -> wallets.saveAndFlush(new PointWallet(user, 0L)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 주문당_Outbox는_하나만_허용한다() {
        Long id = orderService.order(user.getId(), menu.getId()).getOrderId();
        CoffeeOrder order = orders.findById(id).orElseThrow();
        assertThatThrownBy(() -> outboxes.saveAndFlush(new OrderOutbox(order, OutboxStatus.PENDING)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 메뉴_조회_API는_판매_중인_메뉴만_반환한다() throws Exception {
        CoffeeMenu inactive = menus.save(new CoffeeMenu("판매중지", 5000L));
        jdbc.update("update coffee_menus set active = false where id = ?", inactive.getId());
        mvc.perform(get("/api/coffee-menus")).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.menus.length()").value(1))
                .andExpect(jsonPath("$.data.menus[0].name").value("아메리카노"));
    }

    @Test
    void 충전과_잔액_조회_API가_동작한다() throws Exception {
        mvc.perform(post("/api/points/charge").contentType("application/json")
                .content("{\"userId\":" + user.getId() + ",\"amount\":2000}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.balance").value(12000));
        mvc.perform(get("/api/points").param("userId", user.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.balance").value(12000));
    }

    @Test
    void 주문_API는_클라이언트_가격을_사용하지_않는다() throws Exception {
        mvc.perform(post("/api/orders").contentType("application/json")
                .content("{\"userId\":" + user.getId() + ",\"menuId\":" + menu.getId() + ",\"price\":1}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.paidPrice").value(4500))
                .andExpect(jsonPath("$.data.remainingBalance").value(5500))
                .andExpect(jsonPath("$.data.orderedAt").value("2026-10-05T10:00:00Z"));
    }

    @Test
    void 잘못된_충전_금액은_400을_반환한다() throws Exception {
        mvc.perform(post("/api/points/charge").contentType("application/json")
                .content("{\"userId\":" + user.getId() + ",\"amount\":0}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_POINT_AMOUNT"));
        assertThat(histories.count()).isZero();
    }

    @Test
    void 잔액_부족_주문_API는_409를_반환한다() throws Exception {
        jdbc.update("update point_wallets set balance = 1000 where user_id = ?", user.getId());
        mvc.perform(post("/api/orders").contentType("application/json")
                .content("{\"userId\":" + user.getId() + ",\"menuId\":" + menu.getId() + "}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INSUFFICIENT_POINT"));
        assertThat(orders.count()).isZero();
    }

    @Test
    void 존재하지_않는_사용자는_404를_반환한다() throws Exception {
        mvc.perform(get("/api/points").param("userId", "9223372036854775807"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void 사용자_ID가_없거나_형식이_틀리면_400을_반환한다() throws Exception {
        mvc.perform(get("/api/points")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get("/api/points").param("userId", "wrong")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void 인기_메뉴_API는_빈_목록도_정상_응답한다() throws Exception {
        mvc.perform(get("/api/coffee-menus/popular")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.menus").isEmpty());
    }
}
