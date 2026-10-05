package com.example.coffeeshop.domain.order.repository;

public interface PopularMenuProjection {
    Long getMenuId();
    String getName();
    Long getPrice();
    Boolean getActive();
    Long getOrderCount();
}
