package com.example.coffeeshop.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "필수 필드가 없거나 ID 형식이 잘못됨"),
    INVALID_POINT_AMOUNT(HttpStatus.BAD_REQUEST, "충전 금액이 0 이하"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자가 존재하지 않음"),
    WALLET_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자의 포인트 지갑이 없음"),
    MENU_NOT_FOUND(HttpStatus.NOT_FOUND, "주문할 메뉴가 존재하지 않음"),
    MENU_NOT_AVAILABLE(HttpStatus.CONFLICT, "판매 중지된 메뉴를 주문함"),
    INSUFFICIENT_POINT(HttpStatus.CONFLICT, "주문 금액보다 잔액이 적음"),
    POINT_BALANCE_OVERFLOW(HttpStatus.CONFLICT, "충전 후 잔액이 너무 많음"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "예상하지 못한 서버 오류")
    ;

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
