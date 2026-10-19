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
    DISABLED_CLOCK_HAS_TIMING(HttpStatus.BAD_REQUEST, "A disabled clock cannot have an initial time or increment."),
    INVITATION_NOT_FOUND(HttpStatus.NOT_FOUND, "The invitation does not exist."),
    INVITATION_EXPIRED(HttpStatus.GONE, "The invitation has expired."),
    INVITATION_CANCELLED(HttpStatus.GONE, "The invitation was cancelled by its host."),
    INVITATION_FULL(HttpStatus.CONFLICT, "This game is already full."),
    INVITATION_ALREADY_CLAIMED(HttpStatus.CONFLICT, "The invitation has already been claimed."),
    CANNOT_JOIN_OWN_INVITATION(HttpStatus.CONFLICT, "You cannot join your own invitation."),
    ONLINE_GAME_NOT_FOUND(HttpStatus.NOT_FOUND, "The online game does not exist."),
    ONLINE_GAME_ALREADY_STARTED(HttpStatus.CONFLICT, "The game has already started and can no longer be cancelled."),
    GAME_NOT_IN_HISTORY(HttpStatus.NOT_FOUND, "Only completed games appear in match history."),
    ONLINE_GAME_NOT_ACTIVE(HttpStatus.CONFLICT, "The game is not in progress."),
    MOVE_OUT_OF_SEQUENCE(HttpStatus.CONFLICT, "The move does not follow the game's latest move; reload the game."),
    NOT_YOUR_TURN(HttpStatus.CONFLICT, "It is not your turn to move."),
    TIME_EXPIRED(HttpStatus.CONFLICT, "Your clock has run out."),
    DISCONNECT_FORFEIT_DUE(HttpStatus.CONFLICT, "The game has ended by disconnect forfeit; reload the game."),
    ILLEGAL_MOVE(HttpStatus.BAD_REQUEST, "The move is not legal in the current position.");

    private final HttpStatus status;
    private final String message;
}
