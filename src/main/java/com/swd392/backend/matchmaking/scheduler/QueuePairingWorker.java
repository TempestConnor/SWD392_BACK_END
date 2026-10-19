package com.swd392.backend.matchmaking.scheduler;

import com.swd392.backend.matchmaking.model.QueuePairing;
import com.swd392.backend.matchmaking.service.MatchmakingService;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// BR-RATE-013-021: pairs compatible queue requests. Candidates come from a snapshot; each pairing is re-checked and
// committed in its own transaction under both request row locks, so a racing cancel, timeout or competing worker
// (another instance) never matches a request twice. Players learn of the match through their queue heartbeat.
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.matchmaking.sweep-enabled", havingValue = "true", matchIfMissing = true)
public class QueuePairingWorker {

    private final MatchmakingService matchmakingService;

    @Scheduled(fixedDelayString = "${app.matchmaking.pairing-interval}")
    public void pairWaitingRequests() {
        List<QueuePairing> pairings = matchmakingService.findPairingCandidates();
        long paired = pairings.stream()
                .map(pairing -> matchmakingService.pair(pairing.getFirstRequestId(), pairing.getSecondRequestId()))
                .filter(Optional::isPresent)
                .count();
        if (!pairings.isEmpty()) {
            log.info(
                    "[MATCHMAKING] - ACTION: pairWaitingRequests: candidates: {}, paired: {}", pairings.size(), paired);
        }
    }
}
