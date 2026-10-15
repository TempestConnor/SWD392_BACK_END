package com.swd392.backend.onlinegame.scheduler;

import com.swd392.backend.onlinegame.service.OnlineGameService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Records TIMEOUT results for active games whose side to move has run out of time, even when neither
// browser calls the backend again (including after a restart). Each game is re-checked and completed
// under its own row lock and transaction, so a move or another sweep racing it completes the game once.
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.online-game.sweep-enabled", havingValue = "true", matchIfMissing = true)
public class ClockTimeoutSweeper {

    private final OnlineGameService onlineGameService;

    @Scheduled(fixedDelayString = "${app.online-game.sweep-interval}")
    public void completeTimedOutGames() {
        List<Long> gameIds = onlineGameService.findGamesDueForTimeout();
        gameIds.forEach(onlineGameService::completeIfTimedOut);
        if (!gameIds.isEmpty()) {
            log.info("[ONLINE_GAME] - ACTION: completeTimedOutGames: candidates: {}", gameIds.size());
        }
    }
}
