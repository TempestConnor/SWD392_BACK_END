package com.swd392.backend.onlinegame.service;

import com.swd392.backend.onlinegame.model.OnlineGame;

public interface OnlineGameService {

    OnlineGame createPendingGame(OnlineGame game);

    OnlineGame getGame(Long gameId, Long accountId);
}
