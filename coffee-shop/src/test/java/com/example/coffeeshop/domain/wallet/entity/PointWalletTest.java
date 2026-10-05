package com.example.coffeeshop.domain.wallet.entity;

import com.example.coffeeshop.common.exception.BusinessException;
import com.example.coffeeshop.common.exception.ErrorCode;
import com.example.coffeeshop.domain.user.entity.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PointWalletTest {
    private PointWallet wallet(long balance) {
        return new PointWallet(new User("wallet@example.com"), balance);
    }

    @Test
    void 충전하면_잔액이_증가한다() {
        PointWallet wallet = wallet(1000);
        wallet.charge(2000);
        assertThat(wallet.getBalance()).isEqualTo(3000);
    }

    @Test
    void 충전_금액은_양수여야_한다() {
        PointWallet wallet = wallet(1000);
        for (long amount : new long[]{0, -1}) {
            assertThatThrownBy(() -> wallet.charge(amount)).isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_POINT_AMOUNT);
        }
        assertThat(wallet.getBalance()).isEqualTo(1000);
    }

    @Test
    void 충전_후_잔액이_long_범위를_넘으면_변경하지_않는다() {
        PointWallet wallet = wallet(Long.MAX_VALUE - 1);
        assertThatThrownBy(() -> wallet.charge(2)).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.POINT_BALANCE_OVERFLOW);
        assertThat(wallet.getBalance()).isEqualTo(Long.MAX_VALUE - 1);
    }

    @Test
    void 결제하면_잔액이_감소한다() {
        PointWallet wallet = wallet(10000);
        wallet.spend(4500);
        assertThat(wallet.getBalance()).isEqualTo(5500);
    }

    @Test
    void 잔액이_부족하면_차감하지_않는다() {
        PointWallet wallet = wallet(1000);
        assertThatThrownBy(() -> wallet.spend(4500)).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INSUFFICIENT_POINT);
        assertThat(wallet.getBalance()).isEqualTo(1000);
    }

    @Test
    void 초기_잔액은_음수가_될_수_없다() {
        assertThatThrownBy(() -> wallet(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
