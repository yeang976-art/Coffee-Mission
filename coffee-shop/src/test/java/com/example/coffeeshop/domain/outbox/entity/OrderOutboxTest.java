package com.example.coffeeshop.domain.outbox.entity;

import com.example.coffeeshop.domain.menu.entity.CoffeeMenu;
import com.example.coffeeshop.domain.order.entity.CoffeeOrder;
import com.example.coffeeshop.domain.user.entity.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderOutboxTest {

    private OrderOutbox newOutbox() {
        CoffeeOrder order = new CoffeeOrder(new User("test@example.com"), new CoffeeMenu("아메리카노", 4500L), 4500L);
        return new OrderOutbox(order, OutboxStatus.PENDING);
    }

    @Test
    void 새_기록은_전송_대기_상태이며_처리_시작_시각이_없다() {
        OrderOutbox outbox = newOutbox();
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(outbox.getAttemptCount()).isZero();
        assertThat(outbox.getProcessingStartedAt()).isNull();
    }

    @Test
    void 전송을_시도하면_처리_중으로_변경하고_횟수를_증가한다() {
        OrderOutbox outbox = newOutbox();
        outbox.trial();
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PROCESSING);
        assertThat(outbox.getAttemptCount()).isEqualTo(1);
        assertThat(outbox.getProcessingStartedAt()).isNotNull();
    }

    @Test
    void 전송_실패를_기록하면_실패_상태가_된다() {
        OrderOutbox outbox = newOutbox();
        outbox.trial();
        outbox.error("외부 플랫폼 응답 지연");
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(outbox.getLastError()).isEqualTo("외부 플랫폼 응답 지연");
        assertThat(outbox.getNextAttemptAt()).isNotNull();
        assertThat(outbox.getSentAt()).isNull();
    }

    @Test
    void 전송_성공을_기록하면_성공_상태가_된다() {
        OrderOutbox outbox = newOutbox();
        outbox.trial();
        outbox.success();
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(outbox.getSentAt()).isNotNull();
        assertThat(outbox.getNextAttemptAt()).isNull();
    }
}
