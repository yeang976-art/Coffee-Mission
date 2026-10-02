package com.example.coffeeshop.domain.menu.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "coffee_menus")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoffeeMenu {
}
