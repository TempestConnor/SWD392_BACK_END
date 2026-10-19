package com.swd392.backend.matchmaking.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Two open queue requests found compatible in a snapshot; pairing re-checks them under their row locks.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(setterPrefix = "with")
public class QueuePairing {
    private Long firstRequestId;
    private Long secondRequestId;
}
