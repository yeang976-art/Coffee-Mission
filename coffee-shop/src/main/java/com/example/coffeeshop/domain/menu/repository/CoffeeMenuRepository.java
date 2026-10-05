package com.example.coffeeshop.domain.menu.repository;

import com.example.coffeeshop.domain.menu.entity.CoffeeMenu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CoffeeMenuRepository extends JpaRepository<CoffeeMenu, Long> {
    List<CoffeeMenu> findByActiveTrueOrderByIdAsc();
}
