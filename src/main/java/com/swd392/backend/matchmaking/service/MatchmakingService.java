package com.swd392.backend.matchmaking.service;

import com.swd392.backend.matchmaking.model.QueueJoinRequest;
import com.swd392.backend.matchmaking.model.QueuePairing;
import com.swd392.backend.matchmaking.model.QueueRequest;
import java.util.List;
import java.util.Optional;

public interface MatchmakingService {

    QueueRequest join(Long accountId, QueueJoinRequest request);

    QueueRequest heartbeat(Long accountId);

    QueueRequest cancel(Long accountId);

    List<Long> findRequestsDueForTimeout();

    void timeOutIfDue(Long requestId);

    List<QueuePairing> findPairingCandidates();

    Optional<Long> pair(Long firstRequestId, Long secondRequestId);
}
