package com.example.coffeeshop;

import com.example.coffeeshop.domain.order.service.CoffeeOrderService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;

@Tag("mysql")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class MySqlWalletConcurrencyIntegrationTest extends WalletConcurrencyScenarios {
    private static String testUrl() {
        String url = System.getenv("MYSQL_TEST_URL");
        if (url == null || !url.matches("jdbc:mysql://(localhost|127\\.0\\.0\\.1):[0-9]+/coffee_mission_test_[a-zA-Z0-9_]+(\\?.*)?")) {
            throw new IllegalStateException("MYSQL_TEST_URL은 별도로 생성한 로컬 coffee_mission_test_ DB여야 합니다.");
        }
        return url;
    }

    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MySqlWalletConcurrencyIntegrationTest::testUrl);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.username", () -> System.getenv("MYSQL_TEST_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("MYSQL_TEST_PASSWORD"));
    }

    @Test
    void 두_애플리케이션_인스턴스에서도_동시_주문_잔액이_보장된다() throws Exception {
        try (var second = new SpringApplicationBuilder(CoffeeShopApplication.class)
                .web(WebApplicationType.NONE).profiles("test")
                .run("--spring.datasource.url=" + testUrl(),
                        "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                        "--spring.datasource.username=" + System.getenv("MYSQL_TEST_USERNAME"),
                        "--spring.datasource.password=" + System.getenv("MYSQL_TEST_PASSWORD"),
                        "--spring.jpa.hibernate.ddl-auto=none",
                        "--outbox.enabled=false")) {
            verifyConcurrentOrders(List.of(orderService, second.getBean(CoffeeOrderService.class)));
        }
    }
}
