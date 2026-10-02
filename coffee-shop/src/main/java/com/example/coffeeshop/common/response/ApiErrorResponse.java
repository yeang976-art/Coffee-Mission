package com.example.coffeeshop.common.response;

import com.example.coffeeshop.common.exception.ErrorCode;
import lombok.Getter;

@Getter
public class ApiErrorResponse {

    private final boolean success;
    private final String code;
    private final String message;

    private ApiErrorResponse(String code, String message) {
        this.success = false;
        this.code = code;
        this.message = message;
    }

    public static ApiErrorResponse of(ErrorCode errorCode) {
        return new ApiErrorResponse(errorCode.name(), errorCode.getMessage());
    }

    public static ApiErrorResponse of(ErrorCode errorCode, String message) {
        return new ApiErrorResponse(errorCode.name(), message);
    }
}
