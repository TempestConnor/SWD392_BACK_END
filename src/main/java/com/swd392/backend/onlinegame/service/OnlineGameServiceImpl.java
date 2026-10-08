package com.swd392.backend.onlinegame.service;

import com.swd392.backend.account.service.AccountService;
import com.swd392.backend.chess.model.ChessColor;
import com.swd392.backend.chess.model.ChessPosition;
import com.swd392.backend.chess.service.ChessRulesService;
import com.swd392.backend.common.exception.AppErrorMessage;
import com.swd392.backend.common.exception.AppException;
import com.swd392.backend.onlinegame.entity.OnlineGameEntity;
import com.swd392.backend.onlinegame.mapper.OnlineGameEntityMapper;
import com.swd392.backend.onlinegame.model.OnlineGame;
import com.swd392.backend.onlinegame.model.OnlineGameStatus;
import com.swd392.backend.onlinegame.repository.OnlineGameRepository;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class OnlineGameServiceImpl implements OnlineGameService {

    private static final Duration READY_WINDOW = Duration.ofMinutes(3);
    private static final long MILLIS_PER_SECOND = 1000L;
    private static final int NO_MOVES_PLAYED = 0;

    private final OnlineGameRepository onlineGameRepository;
    private final OnlineGameEntityMapper onlineGameEntityMapper;
    private final ChessRulesService chessRulesService;
    private final AccountService accountService;

    @Override
    public OnlineGame createPendingGame(OnlineGame game) {
        ChessPosition initialPosition = chessRulesService.replay(List.of());
        Long remainingMs = game.isClockEnabled() ? game.getInitialSeconds() * MILLIS_PER_SECOND : null;
        game.setStatus(OnlineGameStatus.PENDING);
        game.setReadyDeadlineAt(game.getCreatedAt().plus(READY_WINDOW));
        game.setInitialFen(initialPosition.getFen());
        game.setCurrentFen(initialPosition.getFen());
        game.setLastPly(NO_MOVES_PLAYED);
        game.setWhiteRemainingMs(remainingMs);
        game.setBlackRemainingMs(remainingMs);

        OnlineGameEntity entity = onlineGameEntityMapper.toEntity(game);
        OnlineGameEntity saved = onlineGameRepository.saveAndFlush(entity);
        log.info(
                "[ONLINE_GAME] - ACTION: createPendingGame: gameId: {}, source: {}, rated: {}",
                saved.getId(),
                saved.getSource(),
                saved.isRated());

        return onlineGameEntityMapper.toModel(saved);
    }

    @Override
    public OnlineGame getGame(Long gameId, Long accountId) {
        OnlineGameEntity entity = onlineGameRepository
                .findById(gameId)
                .filter(found -> isParticipant(found, accountId))
                .orElseThrow(() -> new AppException(AppErrorMessage.ONLINE_GAME_NOT_FOUND));

        OnlineGame game = onlineGameEntityMapper.toModel(entity);
        Map<Long, String> displayNames =
                accountService.getDisplayNames(List.of(game.getWhiteAccountId(), game.getBlackAccountId()));
        game.setWhiteDisplayName(displayNames.get(game.getWhiteAccountId()));
        game.setBlackDisplayName(displayNames.get(game.getBlackAccountId()));
        game.setViewerColor(accountId.equals(game.getWhiteAccountId()) ? ChessColor.WHITE : ChessColor.BLACK);

        return game;
    }

    private boolean isParticipant(OnlineGameEntity entity, Long accountId) {
        return accountId.equals(entity.getWhiteAccountId()) || accountId.equals(entity.getBlackAccountId());
    }
}
