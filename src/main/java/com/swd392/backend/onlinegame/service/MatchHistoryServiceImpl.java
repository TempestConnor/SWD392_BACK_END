package com.swd392.backend.onlinegame.service;

import com.swd392.backend.account.service.AccountService;
import com.swd392.backend.chess.model.ChessColor;
import com.swd392.backend.chess.model.GameResult;
import com.swd392.backend.common.exception.AppErrorMessage;
import com.swd392.backend.common.exception.AppException;
import com.swd392.backend.onlinegame.entity.GameMoveEntity;
import com.swd392.backend.onlinegame.entity.OnlineGameEntity;
import com.swd392.backend.onlinegame.mapper.GameMoveEntityMapper;
import com.swd392.backend.onlinegame.mapper.OnlineGameEntityMapper;
import com.swd392.backend.onlinegame.model.GameHistory;
import com.swd392.backend.onlinegame.model.OnlineGame;
import com.swd392.backend.onlinegame.model.OnlineGameStatus;
import com.swd392.backend.onlinegame.model.PlayerResult;
import com.swd392.backend.onlinegame.repository.GameMoveRepository;
import com.swd392.backend.onlinegame.repository.OnlineGameRepository;
import com.swd392.backend.rating.model.RatingEvent;
import com.swd392.backend.rating.service.RatingService;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// F13: match history holds only COMPLETED games (product decision 2026-10-19). Pending and pre-start cancelled
// games never started (BR-INV-018); active games are resumed through the online game endpoints instead.
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MatchHistoryServiceImpl implements MatchHistoryService {

    private static final String COMPLETED = OnlineGameStatus.COMPLETED.name();
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("endedAt"), Sort.Order.desc("id"));

    private final OnlineGameRepository onlineGameRepository;
    private final OnlineGameEntityMapper onlineGameEntityMapper;
    private final GameMoveRepository gameMoveRepository;
    private final GameMoveEntityMapper gameMoveEntityMapper;
    private final AccountService accountService;
    private final RatingService ratingService;

    // Client sort parameters are ignored: history is always newest ending first, game id breaking ties.
    @Override
    public Page<GameHistory> searchHistory(Long accountId, Pageable pageable) {
        Pageable ordered = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), NEWEST_FIRST);
        Page<OnlineGameEntity> games = onlineGameRepository.findParticipantGames(accountId, COMPLETED, ordered);
        List<Long> gameIds = games.stream().map(OnlineGameEntity::getId).toList();
        List<Long> participantIds = games.stream()
                .flatMap(game -> Stream.of(game.getWhiteAccountId(), game.getBlackAccountId()))
                .distinct()
                .toList();
        Map<Long, List<RatingEvent>> ratingEvents =
                ratingService.getGameEvents(gameIds).stream().collect(Collectors.groupingBy(RatingEvent::getGameId));
        HistoryLookup lookup = new HistoryLookup(accountService.getDisplayNames(participantIds), ratingEvents);

        return games.map(entity -> toHistory(entity, accountId, lookup));
    }

    @Override
    public GameHistory getHistoryGame(Long gameId, Long accountId) {
        OnlineGameEntity entity = onlineGameRepository
                .findById(gameId)
                .filter(found -> isParticipant(found, accountId))
                .orElseThrow(() -> new AppException(AppErrorMessage.ONLINE_GAME_NOT_FOUND));
        if (!COMPLETED.equals(entity.getStatus())) {
            throw new AppException(AppErrorMessage.GAME_NOT_IN_HISTORY);
        }
        Map<Long, String> displayNames =
                accountService.getDisplayNames(List.of(entity.getWhiteAccountId(), entity.getBlackAccountId()));
        List<RatingEvent> ratingEvents = ratingService.getGameEvents(List.of(gameId));
        HistoryLookup lookup = new HistoryLookup(displayNames, Map.of(gameId, ratingEvents));
        List<GameMoveEntity> moves = gameMoveRepository.findByIdGameIdOrderByIdPlyAsc(gameId);

        GameHistory history = toHistory(entity, accountId, lookup);
        history.setMoves(gameMoveEntityMapper.toModels(moves));

        return history;
    }

    // BR-HIST-005: display names are read from the current account rather than a snapshot.
    private GameHistory toHistory(OnlineGameEntity entity, Long accountId, HistoryLookup lookup) {
        OnlineGame game = onlineGameEntityMapper.toModel(entity);
        game.setWhiteDisplayName(lookup.displayNames().get(game.getWhiteAccountId()));
        game.setBlackDisplayName(lookup.displayNames().get(game.getBlackAccountId()));
        game.setViewerColor(accountId.equals(game.getWhiteAccountId()) ? ChessColor.WHITE : ChessColor.BLACK);
        List<RatingEvent> events = lookup.ratingEvents().getOrDefault(game.getId(), List.of());

        GameHistory history = GameHistory.builder()
                .withGame(game)
                .withViewerResult(viewerResult(game.getOutcome(), game.getViewerColor()))
                .withWhiteRatingEvent(eventOf(events, game.getWhiteAccountId()))
                .withBlackRatingEvent(eventOf(events, game.getBlackAccountId()))
                .build();

        return history;
    }

    // BR-GAME-013: the stored outcome is per color; history shows it from the viewer's side.
    private PlayerResult viewerResult(GameResult outcome, ChessColor viewerColor) {
        if (outcome == GameResult.DRAW) {
            return PlayerResult.DRAW;
        }
        ChessColor winner = outcome == GameResult.WHITE_WIN ? ChessColor.WHITE : ChessColor.BLACK;

        return winner == viewerColor ? PlayerResult.WIN : PlayerResult.LOSS;
    }

    private RatingEvent eventOf(List<RatingEvent> events, Long accountId) {
        return events.stream()
                .filter(event -> accountId.equals(event.getAccountId()))
                .findFirst()
                .orElse(null);
    }

    private boolean isParticipant(OnlineGameEntity entity, Long accountId) {
        return accountId.equals(entity.getWhiteAccountId()) || accountId.equals(entity.getBlackAccountId());
    }

    private record HistoryLookup(Map<Long, String> displayNames, Map<Long, List<RatingEvent>> ratingEvents) {}
}
