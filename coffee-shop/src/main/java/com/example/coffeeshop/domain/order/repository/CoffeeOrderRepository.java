package com.example.coffeeshop.domain.order.repository;

import com.example.coffeeshop.domain.order.entity.CoffeeOrder;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CoffeeOrderRepository extends JpaRepository<CoffeeOrder, Long> {
    @Query("""
            select m.id as menuId, m.name as name, m.price as price,
                   m.active as active, count(o.id) as orderCount
            from CoffeeOrder o join o.coffeeMenu m
            where o.orderedAt >= :since and o.orderedAt < :until
            group by m.id, m.name, m.price, m.active
            order by count(o.id) desc, m.id asc
            """)
    List<PopularMenuProjection> findPopularMenus(@Param("since") LocalDateTime since,
                                                @Param("until") LocalDateTime until,
                                                Pageable pageable);
}
