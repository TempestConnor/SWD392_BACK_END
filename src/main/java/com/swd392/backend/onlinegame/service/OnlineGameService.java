package com.swd392.backend.onlinegame.service;

import com.swd392.backend.onlinegame.model.GameMove;
import com.swd392.backend.onlinegame.model.MoveSubmission;
import com.swd392.backend.onlinegame.model.OnlineGame;
import java.util.List;

public interface OnlineGameService {

    OnlineGame createPendingGame(OnlineGame game);

    OnlineGame getGame(Long gameId, Long accountId);

    OnlineGame markReady(Long gameId, Long accountId);

    OnlineGame cancelPendingGame(Long gameId, Long accountId);

    OnlineGame recordHeartbeat(Long gameId, Long accountId);

    OnlineGame makeMove(Long gameId, Long accountId, MoveSubmission submission);

    List<GameMove> getMoves(Long gameId, Long accountId);

    OnlineGame resign(Long gameId, Long accountId);

    OnlineGame voteToAbandon(Long gameId, Long accountId);

    List<Long> findGamesDueForPreStartCancel();

    void cancelIfPreStartDue(Long gameId);

    List<Long> findActiveGamesDue();

    void resolveActiveGame(Long gameId);
}
