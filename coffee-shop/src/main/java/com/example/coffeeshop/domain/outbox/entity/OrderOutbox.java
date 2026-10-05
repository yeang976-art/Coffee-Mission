package com.example.coffeeshop.domain.outbox.entity;

import com.example.coffeeshop.common.timestamp.BaseTimeEntity;
import com.example.coffeeshop.domain.order.entity.CoffeeOrder;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "order_outbox")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderOutbox extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private CoffeeOrder coffeeOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @Column(name = "processing_started_at")
    private LocalDateTime processingStartedAt;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    public OrderOutbox(CoffeeOrder coffeeOrder, OutboxStatus status) {
        this.coffeeOrder = coffeeOrder;
        this.status = status;
        this.attemptCount = 0;
        
    }

    public void trial() {
        if (status != OutboxStatus.PENDING && status != OutboxStatus.FAILED) {
            throw new IllegalStateException("전송 대기 또는 실패 기록만 처리할 수 있습니다.");
        }
        this.status = OutboxStatus.PROCESSING;
        this.attemptCount++;
        this.processingStartedAt = LocalDateTime.now(java.time.ZoneOffset.UTC);
        this.nextAttemptAt = null;
    }

    public void error(String lastError) {
        if (status != OutboxStatus.PROCESSING) {
            throw new IllegalStateException("처리 중인 기록만 실패로 변경할 수 있습니다.");
        }
        this.status = OutboxStatus.FAILED;
        this.lastError = lastError;
        this.nextAttemptAt = LocalDateTime.now(java.time.ZoneOffset.UTC);
    }

    public void success() {
        if (status != OutboxStatus.PROCESSING) {
            throw new IllegalStateException("처리 중인 기록만 성공으로 변경할 수 있습니다.");
        }
        this.status = OutboxStatus.SENT;
        this.lastError = null;
        this.nextAttemptAt = null;
        this.sentAt = LocalDateTime.now(java.time.ZoneOffset.UTC);
    }
}
