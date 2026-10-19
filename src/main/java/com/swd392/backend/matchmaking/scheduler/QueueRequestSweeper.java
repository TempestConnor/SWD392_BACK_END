package com.swd392.backend.matchmaking.scheduler;

import com.swd392.backend.matchmaking.service.MatchmakingService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// BR-RATE-019: closes silent queue requests as TIMED_OUT even when the browser never calls the backend again.
// Each request is re-checked and closed under its own row lock and transaction.
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.matchmaking.sweep-enabled", havingValue = "true", matchIfMissing = true)
public class QueueRequestSweeper {

    private final MatchmakingService matchmakingService;

    @Scheduled(fixedDelayString = "${app.matchmaking.sweep-interval}")
    public void timeOutSilentRequests() {
        List<Long> requestIds = matchmakingService.findRequestsDueForTimeout();
        requestIds.forEach(matchmakingService::timeOutIfDue);
        if (!requestIds.isEmpty()) {
            log.info("[MATCHMAKING] - ACTION: timeOutSilentRequests: candidates: {}", requestIds.size());
        }
    }
}
