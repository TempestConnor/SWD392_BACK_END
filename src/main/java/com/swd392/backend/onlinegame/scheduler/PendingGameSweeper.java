package com.swd392.backend.onlinegame.scheduler;

import com.swd392.backend.onlinegame.service.OnlineGameService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Cancels pending games past their Ready deadline or with a silent participant even when no browser
// calls the backend again. Each game is re-checked and cancelled under its own row lock and transaction.
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.online-game.sweep-enabled", havingValue = "true", matchIfMissing = true)
public class PendingGameSweeper {

    private final OnlineGameService onlineGameService;

    @Scheduled(fixedDelayString = "${app.online-game.sweep-interval}")
    public void cancelDuePendingGames() {
        List<Long> gameIds = onlineGameService.findGamesDueForPreStartCancel();
        gameIds.forEach(onlineGameService::cancelIfPreStartDue);
        if (!gameIds.isEmpty()) {
            log.info("[ONLINE_GAME] - ACTION: cancelDuePendingGames: candidates: {}", gameIds.size());
        }
    }
}
