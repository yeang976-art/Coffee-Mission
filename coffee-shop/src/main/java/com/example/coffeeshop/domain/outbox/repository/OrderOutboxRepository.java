package com.example.coffeeshop.domain.outbox.repository;

import com.example.coffeeshop.domain.outbox.entity.OrderOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderOutboxRepository extends JpaRepository<OrderOutbox, Long> {
}
