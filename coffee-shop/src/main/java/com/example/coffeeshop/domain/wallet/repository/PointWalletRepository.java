package com.example.coffeeshop.domain.wallet.repository;

import com.example.coffeeshop.domain.wallet.entity.PointWallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PointWalletRepository extends JpaRepository<PointWallet, Long> {
}
