package com.example.coffeeshop;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class WalletConcurrencyIntegrationTest extends WalletConcurrencyScenarios {
}
