package com.swd392.backend.onlinegame.service;

import com.swd392.backend.onlinegame.model.GameHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MatchHistoryService {

    Page<GameHistory> searchHistory(Long accountId, Pageable pageable);

    GameHistory getHistoryGame(Long gameId, Long accountId);
}
