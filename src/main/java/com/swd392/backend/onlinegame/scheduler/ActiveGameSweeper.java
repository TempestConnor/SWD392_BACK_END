package com.swd392.backend.onlinegame.scheduler;

import com.swd392.backend.onlinegame.service.OnlineGameService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Records TIMEOUT and DISCONNECT results for active games, and marks silent participants as disconnected,
// even when neither browser calls the backend again (including after a restart). Each game is re-checked
// under its own row lock and transaction, so a move, heartbeat or another sweep racing it completes it once.
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.online-game.sweep-enabled", havingValue = "true", matchIfMissing = true)
public class ActiveGameSweeper {

    private final OnlineGameService onlineGameService;

    @Scheduled(fixedDelayString = "${app.online-game.sweep-interval}")
    public void resolveDueActiveGames() {
        List<Long> gameIds = onlineGameService.findActiveGamesDue();
        gameIds.forEach(onlineGameService::resolveActiveGame);
        if (!gameIds.isEmpty()) {
            log.info("[ONLINE_GAME] - ACTION: resolveDueActiveGames: candidates: {}", gameIds.size());
        }
    }
}
