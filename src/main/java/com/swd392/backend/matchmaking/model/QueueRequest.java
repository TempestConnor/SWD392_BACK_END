package com.swd392.backend.matchmaking.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(setterPrefix = "with")
public class QueueRequest {
    private Long id;
    private Long accountId;
    private String modeCode;
    private String variantCode;
    private int ratingAtEntry;
    private QueueStatus status;
    private Instant joinedAt;
    private Instant heartbeatAt;
    private Instant leftAt;
    private Long gameId;
    private Integer heartbeatIntervalSeconds;
}
