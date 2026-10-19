package com.swd392.backend.matchmaking.model;

// QUEUED is an open request (left_at IS NULL); the others mirror the stored exit_reason of a closed one.
public enum QueueStatus {
    QUEUED,
    MATCHED,
    CANCELLED,
    TIMED_OUT
}
