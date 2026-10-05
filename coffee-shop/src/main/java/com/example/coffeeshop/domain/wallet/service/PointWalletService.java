package com.example.coffeeshop.domain.wallet.service;

import com.example.coffeeshop.common.exception.BusinessException;
import com.example.coffeeshop.common.exception.ErrorCode;
import com.example.coffeeshop.domain.history.entity.PointHistory;
import com.example.coffeeshop.domain.history.entity.PointHistoryType;
import com.example.coffeeshop.domain.history.repository.PointHistoryRepository;
import com.example.coffeeshop.domain.user.repository.UserRepository;
import com.example.coffeeshop.domain.wallet.dto.PointBalanceResponse;
import com.example.coffeeshop.domain.wallet.dto.PointChargeResponse;
import com.example.coffeeshop.domain.wallet.entity.PointWallet;
import com.example.coffeeshop.domain.wallet.repository.PointWalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PointWalletService {
    private final UserRepository userRepository;
    private final PointWalletRepository pointWalletRepository;
    private final PointHistoryRepository pointHistoryRepository;

    @Transactional(readOnly = true)
    public PointBalanceResponse getBalance(Long userId) {
        validateUser(userId);
        PointWallet wallet = pointWalletRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));
        return new PointBalanceResponse(userId, wallet.getBalance());
    }

    @Transactional
    public PointChargeResponse charge(Long userId, long amount) {
        if (amount <= 0) {
            throw new BusinessException(ErrorCode.INVALID_POINT_AMOUNT);
        }
        validateUser(userId);
        PointWallet wallet = pointWalletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));
        wallet.charge(amount);
        pointHistoryRepository.save(new PointHistory(wallet, null, PointHistoryType.CHARGE, amount, wallet.getBalance()));
        return new PointChargeResponse(userId, amount, wallet.getBalance());
    }

    private void validateUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
    }
}
