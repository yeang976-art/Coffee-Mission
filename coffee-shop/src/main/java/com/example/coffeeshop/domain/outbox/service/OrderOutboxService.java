package com.example.coffeeshop.domain.outbox.service;

import com.example.coffeeshop.domain.outbox.client.OrderCollectorClient;
import com.example.coffeeshop.domain.outbox.dto.OutboxDelivery;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "outbox.enabled", havingValue = "true")
public class OrderOutboxService {
    private final OrderOutboxTransactionService transactions;
    private final OrderCollectorClient collector;

    public void send(Long id) {
        Optional<OutboxDelivery> claimed = transactions.claim(id);
        if (claimed.isEmpty()) {
            return;
        }
        OutboxDelivery delivery = claimed.get();
        try {
            collector.send(delivery.payload());
        } catch (RuntimeException exception) {
            transactions.fail(delivery, exception.getMessage());
            return;
        }
        transactions.complete(delivery);
    }
}
