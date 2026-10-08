package com.swd392.backend.chess.engine;

public class EngineFailureException extends RuntimeException {

    public EngineFailureException(String message) {
        super(message);
    }

    public EngineFailureException(String message, Throwable cause) {
        super(message, cause);
    }
}
