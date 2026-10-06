package com.example.coffeeshop.domain.outbox.service;

import com.example.coffeeshop.domain.order.entity.CoffeeOrder;
import com.example.coffeeshop.domain.outbox.config.OutboxProperties;
import com.example.coffeeshop.domain.outbox.dto.OrderTransmission;
import com.example.coffeeshop.domain.outbox.dto.OutboxDelivery;
import com.example.coffeeshop.domain.outbox.entity.OrderOutbox;
import com.example.coffeeshop.domain.outbox.entity.OutboxStatus;
import com.example.coffeeshop.domain.outbox.repository.OrderOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "outbox.enabled", havingValue = "true")
public class OrderOutboxTransactionService {
    private final OrderOutboxRepository repository;
    private final OutboxProperties properties;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<Long> findReadyIds() {
        LocalDateTime now = LocalDateTime.now(clock);
        return repository.findReadyIds(OutboxStatus.PENDING, OutboxStatus.FAILED, OutboxStatus.PROCESSING,
                now, now.minusSeconds(properties.leaseSeconds()), PageRequest.of(0, properties.batchSize()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<OutboxDelivery> claim(Long id) {
        Optional<OrderOutbox> found = repository.findByIdForUpdate(id);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        OrderOutbox outbox = found.get();
        LocalDateTime now = LocalDateTime.now(clock);
        if (outbox.getStatus() == OutboxStatus.SENT) {
            return Optional.empty();
        }
        if (outbox.getStatus() == OutboxStatus.PROCESSING) {
            if (outbox.getProcessingStartedAt().isAfter(now.minusSeconds(properties.leaseSeconds()))) {
                return Optional.empty();
            }
            outbox.error("처리 기한이 지나 전송을 다시 시도합니다.", now);
        }
        if (outbox.getNextAttemptAt() != null && outbox.getNextAttemptAt().isAfter(now)) {
            return Optional.empty();
        }
        outbox.trial(now);
        CoffeeOrder order = outbox.getCoffeeOrder();
        OrderTransmission payload = new OrderTransmission(outbox.getId(), order.getId(),
                order.getUser().getId(), order.getCoffeeMenu().getId(), order.getPaidPrice());
        return Optional.of(new OutboxDelivery(payload, outbox.getAttemptCount()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(OutboxDelivery delivery) {
        repository.findByIdForUpdate(delivery.payload().eventId())
                .filter(outbox -> isCurrentAttempt(outbox, delivery))
                .ifPresent(outbox -> outbox.success(LocalDateTime.now(clock)));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(OutboxDelivery delivery, String error) {
        repository.findByIdForUpdate(delivery.payload().eventId())
                .filter(outbox -> isCurrentAttempt(outbox, delivery))
                .ifPresent(outbox -> outbox.error(error,
                        LocalDateTime.now(clock).plusSeconds(properties.retryDelaySeconds())));
    }

    private boolean isCurrentAttempt(OrderOutbox outbox, OutboxDelivery delivery) {
        return outbox.getStatus() == OutboxStatus.PROCESSING
                && outbox.getAttemptCount() == delivery.attemptCount();
    }
}
