package com.example.coffeeshop.domain.outbox.repository;

import com.example.coffeeshop.domain.outbox.entity.OrderOutbox;
import com.example.coffeeshop.domain.outbox.entity.OutboxStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderOutboxRepository extends JpaRepository<OrderOutbox, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrderOutbox o where o.id = :id")
    Optional<OrderOutbox> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select o.id from OrderOutbox o
            where o.status = :pending
               or (o.status = :failed and (o.nextAttemptAt is null or o.nextAttemptAt <= :now))
               or (o.status = :processing and o.processingStartedAt <= :expiredAt)
            order by o.id
            """)
    List<Long> findReadyIds(@Param("pending") OutboxStatus pending,
                            @Param("failed") OutboxStatus failed,
                            @Param("processing") OutboxStatus processing,
                            @Param("now") LocalDateTime now,
                            @Param("expiredAt") LocalDateTime expiredAt,
                            Pageable pageable);
}
