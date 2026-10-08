package com.swd392.backend.common.exception;

import lombok.Getter;

@Getter
public class AppException extends RuntimeException {

    private final AppErrorMessage errorMessage;

    public AppException(AppErrorMessage errorMessage) {
        super(errorMessage.getMessage());
        this.errorMessage = errorMessage;
    }

    public AppException(AppErrorMessage errorMessage, Throwable cause) {
        super(errorMessage.getMessage(), cause);
        this.errorMessage = errorMessage;
    }
}
