package com.example.coffeeshop.domain.history.entity;

import com.example.coffeeshop.domain.order.entity.CoffeeOrder;
import com.example.coffeeshop.domain.wallet.entity.PointWallet;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@EntityListeners(AuditingEntityListener.class)
@Getter
@Entity
@Table(name = "point_histories")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointHistory {
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", nullable = false)
    private PointWallet pointWallet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", unique = true)
    private CoffeeOrder coffeeOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PointHistoryType type;

    @Column(nullable = false)
    private Long amount;

    @Column(name = "balance_after", nullable = false)
    private Long balanceAfter;

    public PointHistory(
            PointWallet pointWallet,
            CoffeeOrder coffeeOrder,
            PointHistoryType type,
            Long amount,
            Long balanceAfter
    ) {
        this.pointWallet = pointWallet;
        this.coffeeOrder = coffeeOrder;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
    }
}
