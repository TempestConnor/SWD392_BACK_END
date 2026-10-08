package com.swd392.backend.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AppErrorMessage {
    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "An account with this email already exists."),
    DISPLAY_NAME_TAKEN(HttpStatus.CONFLICT, "This display name is already taken."),
    PASSWORD_TOO_LONG(HttpStatus.BAD_REQUEST, "Password must not exceed 72 bytes when UTF-8 encoded."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Email or password is incorrect."),
    CURRENT_ACCOUNT_NOT_FOUND(HttpStatus.UNAUTHORIZED, "The authenticated account no longer exists."),
    ILLEGAL_MOVE_SEQUENCE(HttpStatus.BAD_REQUEST, "The move list contains an illegal move."),
    NOT_CPU_TURN(HttpStatus.CONFLICT, "It is not the CPU's turn to move."),
    ENGINE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "The chess engine is unavailable; try again shortly."),
    DISABLED_CLOCK_HAS_TIMING(HttpStatus.BAD_REQUEST, "A disabled clock cannot have an initial time or increment.");

    private final HttpStatus status;
    private final String message;
}
