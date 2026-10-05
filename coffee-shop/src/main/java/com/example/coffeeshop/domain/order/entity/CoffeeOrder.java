package com.example.coffeeshop.domain.order.entity;

import com.example.coffeeshop.domain.menu.entity.CoffeeMenu;
import com.example.coffeeshop.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "coffee_orders", indexes = @Index(name = "idx_orders_ordered_at_menu", columnList = "ordered_at, menu_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoffeeOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "menu_id", nullable = false)
    private CoffeeMenu coffeeMenu;

    @Column(name = "paid_price", nullable = false)
    private Long paidPrice;

    @Column(name = "ordered_at", nullable = false)
    private LocalDateTime orderedAt;

    public CoffeeOrder(User user, CoffeeMenu coffeeMenu, Long paidPrice) {
        this(user, coffeeMenu, paidPrice, LocalDateTime.now(java.time.ZoneOffset.UTC));
    }

    public CoffeeOrder(User user, CoffeeMenu coffeeMenu, Long paidPrice, LocalDateTime orderedAt) {
        this.user = user;
        this.coffeeMenu = coffeeMenu;
        this.paidPrice = paidPrice;
        this.orderedAt = orderedAt;
    }
}
