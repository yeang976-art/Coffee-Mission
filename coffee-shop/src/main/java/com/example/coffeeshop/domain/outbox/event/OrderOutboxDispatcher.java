package com.example.coffeeshop.domain.outbox.event;

import com.example.coffeeshop.domain.outbox.service.OrderOutboxService;
import com.example.coffeeshop.domain.outbox.service.OrderOutboxTransactionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.RejectedExecutionException;

@Slf4j
@Component
@ConditionalOnProperty(name = "outbox.enabled", havingValue = "true")
public class OrderOutboxDispatcher {
    private final OrderOutboxService service;
    private final OrderOutboxTransactionService transactions;
    private final ThreadPoolTaskExecutor executor;

    public OrderOutboxDispatcher(OrderOutboxService service, OrderOutboxTransactionService transactions,
                                 @Qualifier("outboxExecutor") ThreadPoolTaskExecutor executor) {
        this.service = service;
        this.transactions = transactions;
        this.executor = executor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommitted(OrderOutboxCreatedEvent event) {
        try {
            executor.execute(() -> sendSafely(event.outboxId()));
        } catch (RejectedExecutionException exception) {
            log.warn("Outbox 실행 대기열이 가득 찼습니다. 주기 작업에서 다시 확인합니다.");
        }
    }

    @Scheduled(fixedDelayString = "${outbox.poll-delay-ms:1000}",
               initialDelayString = "${outbox.poll-initial-delay-ms:1000}")
    public void poll() {
        for (Long id : transactions.findReadyIds()) {
            sendSafely(id);
        }
    }

    private void sendSafely(Long id) {
        try {
            service.send(id);
        } catch (RuntimeException exception) {
            log.error("Outbox {} 처리 중 오류가 발생했습니다. 다음 주기에서 복구합니다.", id, exception);
        }
    }
}
