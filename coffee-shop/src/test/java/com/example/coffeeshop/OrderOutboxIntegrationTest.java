package com.example.coffeeshop;

import com.example.coffeeshop.domain.history.repository.PointHistoryRepository;
import com.example.coffeeshop.domain.menu.entity.CoffeeMenu;
import com.example.coffeeshop.domain.menu.repository.CoffeeMenuRepository;
import com.example.coffeeshop.domain.order.entity.CoffeeOrder;
import com.example.coffeeshop.domain.order.repository.CoffeeOrderRepository;
import com.example.coffeeshop.domain.order.service.CoffeeOrderService;
import com.example.coffeeshop.domain.outbox.client.OrderCollectorClient;
import com.example.coffeeshop.domain.outbox.entity.OrderOutbox;
import com.example.coffeeshop.domain.outbox.entity.OutboxStatus;
import com.example.coffeeshop.domain.outbox.repository.OrderOutboxRepository;
import com.example.coffeeshop.domain.outbox.service.OrderOutboxService;
import com.example.coffeeshop.domain.outbox.service.OrderOutboxTransactionService;
import com.example.coffeeshop.domain.user.entity.User;
import com.example.coffeeshop.domain.user.repository.UserRepository;
import com.example.coffeeshop.domain.wallet.entity.PointWallet;
import com.example.coffeeshop.domain.wallet.repository.PointWalletRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "outbox.enabled=true", "outbox.poll-initial-delay-ms=3600000", "outbox.request-timeout-millis=200"
})
@ActiveProfiles("test")
class OrderOutboxIntegrationTest {
    private static final Instant BASE = Instant.parse("2026-10-06T10:00:00Z");
    private static final AtomicInteger RESPONSE_STATUS = new AtomicInteger(200);
    private static final AtomicInteger RESPONSE_DELAY = new AtomicInteger();
    private static final ConcurrentLinkedQueue<Received> RECEIVED = new ConcurrentLinkedQueue<>();
    private static final Set<String> PROCESSED_EVENTS = ConcurrentHashMap.newKeySet();
    private static final java.util.concurrent.ExecutorService SERVER_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
    private static final HttpServer SERVER = startServer();

    @Autowired UserRepository users;
    @Autowired CoffeeMenuRepository menus;
    @Autowired PointWalletRepository wallets;
    @Autowired PointHistoryRepository histories;
    @Autowired CoffeeOrderRepository orders;
    @Autowired OrderOutboxRepository outboxes;
    @Autowired CoffeeOrderService orderService;
    @Autowired OrderOutboxService outboxService;
    @Autowired OrderOutboxTransactionService transactions;
    @Autowired OrderCollectorClient collector;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;
    private User user;
    private CoffeeMenu menu;

    private record Received(String body, String eventId) {
    }

    private static HttpServer startServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/orders", exchange -> {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String eventId = exchange.getRequestHeaders().getFirst("Idempotency-Key");
                RECEIVED.add(new Received(body, eventId));
                int status = RESPONSE_STATUS.get();
                if (status == 200) {
                    PROCESSED_EVENTS.add(eventId);
                }
                try {
                    Thread.sleep(RESPONSE_DELAY.get());
                    exchange.sendResponseHeaders(status, -1);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } catch (IOException ignored) {
                    // 응답 시간 초과 테스트에서는 클라이언트가 먼저 연결을 닫습니다.
                } finally {
                    exchange.close();
                }
            });
            server.setExecutor(SERVER_EXECUTOR);
            server.start();
            return server;
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @DynamicPropertySource
    static void collectorUrl(DynamicPropertyRegistry registry) {
        registry.add("outbox.url", () -> "http://127.0.0.1:" + SERVER.getAddress().getPort() + "/orders");
    }

    @AfterAll
    static void stopServer() {
        SERVER.stop(0);
        SERVER_EXECUTOR.shutdownNow();
    }

    @TestConfiguration
    static class TimeConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }

    static class MutableClock extends Clock {
        private final AtomicReference<Instant> current = new AtomicReference<>(BASE);

        void reset() {
            current.set(BASE);
        }

        void advance(long seconds) {
            current.updateAndGet(time -> time.plusSeconds(seconds));
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant(), zone);
        }

        @Override
        public Instant instant() {
            return current.get();
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
        RESPONSE_STATUS.set(200);
        RESPONSE_DELAY.set(0);
        RECEIVED.clear();
        PROCESSED_EVENTS.clear();
        clock.reset();
        user = users.save(new User("outbox@example.com"));
        wallets.save(new PointWallet(user, 10000L));
        menu = menus.save(new CoffeeMenu("아메리카노", 4500L));
    }

    private Long pendingFixture() {
        CoffeeOrder order = orders.save(new CoffeeOrder(user, menu, 4500L, LocalDateTime.now(clock)));
        return outboxes.save(new OrderOutbox(order, OutboxStatus.PENDING)).getId();
    }

    private OrderOutbox awaitStatus(Long id, OutboxStatus expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            OrderOutbox outbox = outboxes.findById(id).orElseThrow();
            if (outbox.getStatus() == expected) {
                return outbox;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Outbox 상태가 " + expected + "로 변경되지 않았습니다.");
    }

    @Test
    void 주문_커밋_후_주문_데이터를_실제로_HTTP_전송한다() throws Exception {
        var response = orderService.order(user.getId(), menu.getId());
        Long id = outboxes.findAll().getFirst().getId();
        OrderOutbox result = awaitStatus(id, OutboxStatus.SENT);
        assertThat(result.getAttemptCount()).isEqualTo(1);
        assertThat(result.getSentAt()).isEqualTo(LocalDateTime.now(clock));
        Received received = RECEIVED.element();
        assertThat(received.body()).contains("\"eventId\":" + id, "\"orderId\":" + response.getOrderId(),
                "\"userId\":" + user.getId(), "\"menuId\":" + menu.getId(), "\"paidPrice\":4500");
        assertThat(received.eventId()).isEqualTo(id.toString());
        assertThat(RECEIVED).hasSize(1);
    }

    @Test
    void 주문_트랜잭션이_롤백되면_외부_전송도_발생하지_않는다() {
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).execute(status -> {
            orderService.order(user.getId(), menu.getId());
            throw new IllegalStateException("커밋 전 실패");
        })).hasMessage("커밋 전 실패");
        assertThat(orders.count()).isZero();
        assertThat(histories.count()).isZero();
        assertThat(outboxes.count()).isZero();
        assertThat(RECEIVED).isEmpty();
        assertThat(wallets.findByUserId(user.getId()).orElseThrow().getBalance()).isEqualTo(10000);
    }

    @Test
    void 외부_500_오류는_결제를_유지하고_재시도_후_성공한다() throws Exception {
        RESPONSE_STATUS.set(500);
        var response = orderService.order(user.getId(), menu.getId());
        Long id = outboxes.findAll().getFirst().getId();
        OrderOutbox failed = awaitStatus(id, OutboxStatus.FAILED);
        assertThat(failed.getNextAttemptAt()).isEqualTo(LocalDateTime.now(clock).plusSeconds(5));
        assertThat(failed.getLastError()).contains("500");
        assertThat(orders.findById(response.getOrderId())).isPresent();
        assertThat(histories.count()).isEqualTo(1);
        assertThat(wallets.findByUserId(user.getId()).orElseThrow().getBalance()).isEqualTo(5500);

        jdbc.update("update coffee_menus set price = 6000 where id = ?", menu.getId());
        RESPONSE_STATUS.set(200);
        clock.advance(5);
        assertThat(transactions.findReadyIds()).contains(id);
        outboxService.send(id);
        OrderOutbox sent = outboxes.findById(id).orElseThrow();
        assertThat(sent.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(sent.getAttemptCount()).isEqualTo(2);
        assertThat(sent.getLastError()).isNull();
        assertThat(RECEIVED).hasSize(2);
        assertThat(RECEIVED).allSatisfy(request -> {
            assertThat(request.eventId()).isEqualTo(id.toString());
            assertThat(request.body()).contains("\"paidPrice\":4500");
        });
    }

    @Test
    void 다음_시도_시각_전에는_재전송하지_않는다() {
        Long id = pendingFixture();
        RESPONSE_STATUS.set(503);
        outboxService.send(id);
        RESPONSE_STATUS.set(200);
        clock.advance(4);
        assertThat(transactions.findReadyIds()).doesNotContain(id);
        outboxService.send(id);
        assertThat(RECEIVED).hasSize(1);
        assertThat(outboxes.findById(id).orElseThrow().getAttemptCount()).isEqualTo(1);
    }

    @Test
    void 여러_작업자가_같은_기록을_요청해도_한_작업자만_전송한다() throws Exception {
        Long id = pendingFixture();
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(10)) {
            var futures = new ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 10; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    outboxService.send(id);
                    return null;
                }));
            }
            start.countDown();
            for (var future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        }
        assertThat(RECEIVED).hasSize(1);
        assertThat(outboxes.findById(id).orElseThrow().getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(outboxes.findById(id).orElseThrow().getAttemptCount()).isEqualTo(1);
    }

    @Test
    void 처리_기한이_지난_기록을_복구하고_이전_작업의_결과를_무시한다() {
        Long id = pendingFixture();
        var oldDelivery = transactions.claim(id).orElseThrow();
        assertThat(transactions.claim(id)).isEmpty();
        assertThat(transactions.findReadyIds()).doesNotContain(id);
        clock.advance(60);
        assertThat(transactions.findReadyIds()).contains(id);
        var newDelivery = transactions.claim(id).orElseThrow();
        assertThat(newDelivery.attemptCount()).isEqualTo(2);
        transactions.complete(oldDelivery);
        transactions.fail(oldDelivery, "오래된 작업의 오류");
        assertThat(outboxes.findById(id).orElseThrow().getStatus()).isEqualTo(OutboxStatus.PROCESSING);
        transactions.complete(newDelivery);
        assertThat(outboxes.findById(id).orElseThrow().getStatus()).isEqualTo(OutboxStatus.SENT);
    }

    @Test
    void 전송_후_상태_기록_전에_중단되어도_같은_이벤트_ID로_재전송한다() {
        Long id = pendingFixture();
        var delivery = transactions.claim(id).orElseThrow();
        collector.send(delivery.payload());
        // 수신은 성공했지만 SENT를 기록하기 전에 중단된 상황을 재현합니다.
        clock.advance(61);
        outboxService.send(id);
        assertThat(RECEIVED).hasSize(2);
        assertThat(PROCESSED_EVENTS).hasSize(1);
        assertThat(RECEIVED).allSatisfy(request -> assertThat(request.eventId()).isEqualTo(id.toString()));
        assertThat(outboxes.findById(id).orElseThrow().getStatus()).isEqualTo(OutboxStatus.SENT);
    }

    @Test
    void 긴_오류_메시지는_컬럼_길이_안에서_저장한다() {
        Long id = pendingFixture();
        var delivery = transactions.claim(id).orElseThrow();
        transactions.fail(delivery, "오류".repeat(1000));
        assertThat(outboxes.findById(id).orElseThrow().getLastError()).hasSize(1000);
        assertThat(outboxes.findById(id).orElseThrow().getStatus()).isEqualTo(OutboxStatus.FAILED);
    }


    @Test
    void 외부_3xx_응답은_전송_성공으로_기록하지_않는다() {
        Long id = pendingFixture();
        RESPONSE_STATUS.set(302);
        outboxService.send(id);
        OrderOutbox result = outboxes.findById(id).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(result.getSentAt()).isNull();
        assertThat(result.getLastError()).contains("302");
    }

    @Test
    void 외부_응답_시간_초과는_실패_기록으로_남긴다() {
        Long id = pendingFixture();
        RESPONSE_DELAY.set(400);
        outboxService.send(id);
        OrderOutbox failed = outboxes.findById(id).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(failed.getNextAttemptAt()).isNotNull();
        assertThat(failed.getLastError()).isNotBlank();
    }
}
