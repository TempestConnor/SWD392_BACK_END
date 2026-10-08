package com.swd392.backend.onlinegame.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swd392.backend.account.service.AccountService;
import com.swd392.backend.chess.model.ChessColor;
import com.swd392.backend.chess.service.ChessRulesServiceImpl;
import com.swd392.backend.common.exception.AppErrorMessage;
import com.swd392.backend.common.exception.AppException;
import com.swd392.backend.onlinegame.entity.OnlineGameEntity;
import com.swd392.backend.onlinegame.mapper.OnlineGameEntityMapper;
import com.swd392.backend.onlinegame.model.GameSource;
import com.swd392.backend.onlinegame.model.OnlineGame;
import com.swd392.backend.onlinegame.model.OnlineGameStatus;
import com.swd392.backend.onlinegame.repository.OnlineGameRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OnlineGameServiceImplTest {

    private static final Instant CLAIMED_AT = Instant.parse("2026-10-08T10:00:00.123Z");
    private static final String START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    private static final Long GAME_ID = 100L;
    private static final Long WHITE_ID = 1L;
    private static final Long BLACK_ID = 2L;
    private static final Long OUTSIDER_ID = 3L;

    @Mock
    private OnlineGameRepository onlineGameRepository;

    @Spy
    private OnlineGameEntityMapper onlineGameEntityMapper = Mappers.getMapper(OnlineGameEntityMapper.class);

    @Spy
    private ChessRulesServiceImpl chessRulesService = new ChessRulesServiceImpl();

    @Mock
    private AccountService accountService;

    @InjectMocks
    private OnlineGameServiceImpl onlineGameService;

    @Test
    void create_pending_game_sets_ready_deadline_start_position_and_full_clocks_ok() {
        // given
        var game = newGame(true, 1800, 10);
        when(onlineGameRepository.saveAndFlush(any(OnlineGameEntity.class))).thenAnswer(invocation -> {
            OnlineGameEntity entity = invocation.getArgument(0);
            entity.setId(GAME_ID);
            return entity;
        });
        var captor = ArgumentCaptor.forClass(OnlineGameEntity.class);

        // when
        var created = onlineGameService.createPendingGame(game);

        // then
        verify(onlineGameRepository).saveAndFlush(captor.capture());
        var saved = captor.getValue();
        assertAll(
                () -> assertThat(created.getId()).isEqualTo(GAME_ID),
                () -> assertThat(saved.getStatus()).isEqualTo("PENDING"),
                () -> assertThat(saved.getSource()).isEqualTo("INVITE"),
                () -> assertThat(saved.getCreatedAt()).isEqualTo(CLAIMED_AT),
                () -> assertThat(saved.getReadyDeadlineAt()).isEqualTo(CLAIMED_AT.plusSeconds(180)),
                () -> assertThat(saved.getInitialFen()).isEqualTo(START_FEN),
                () -> assertThat(saved.getCurrentFen()).isEqualTo(START_FEN),
                () -> assertThat(saved.getLastPly()).isZero(),
                () -> assertThat(saved.getWhiteRemainingMs()).isEqualTo(1_800_000L),
                () -> assertThat(saved.getBlackRemainingMs()).isEqualTo(1_800_000L),
                () -> assertThat(saved.getStartedAt()).isNull());
    }

    @Test
    void create_pending_game_without_clock_has_no_remaining_time_ok() {
        // given
        var game = newGame(false, null, null);
        when(onlineGameRepository.saveAndFlush(any(OnlineGameEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        var captor = ArgumentCaptor.forClass(OnlineGameEntity.class);

        // when
        onlineGameService.createPendingGame(game);

        // then
        verify(onlineGameRepository).saveAndFlush(captor.capture());
        var saved = captor.getValue();
        assertAll(
                () -> assertThat(saved.isClockEnabled()).isFalse(),
                () -> assertThat(saved.getWhiteRemainingMs()).isNull(),
                () -> assertThat(saved.getBlackRemainingMs()).isNull());
    }

    @Test
    void get_game_for_black_participant_includes_names_and_viewer_color_ok() {
        // given
        when(onlineGameRepository.findById(GAME_ID)).thenReturn(Optional.of(storedGame()));
        when(accountService.getDisplayNames(anyCollection())).thenReturn(Map.of(WHITE_ID, "Alex", BLACK_ID, "alex"));

        // when
        var game = onlineGameService.getGame(GAME_ID, BLACK_ID);

        // then
        assertAll(
                () -> assertThat(game.getStatus()).isEqualTo(OnlineGameStatus.PENDING),
                () -> assertThat(game.getWhiteDisplayName()).isEqualTo("Alex"),
                () -> assertThat(game.getBlackDisplayName()).isEqualTo("alex"),
                () -> assertThat(game.getViewerColor()).isEqualTo(ChessColor.BLACK));
    }

    @Test
    void get_game_by_non_participant_is_not_found_ko() {
        // given
        when(onlineGameRepository.findById(GAME_ID)).thenReturn(Optional.of(storedGame()));

        // when / then
        assertThatThrownBy(() -> onlineGameService.getGame(GAME_ID, OUTSIDER_ID))
                .isInstanceOf(AppException.class)
                .extracting("errorMessage")
                .isEqualTo(AppErrorMessage.ONLINE_GAME_NOT_FOUND);
        verify(accountService, never()).getDisplayNames(anyCollection());
    }

    @Test
    void get_missing_game_is_not_found_ko() {
        // given
        when(onlineGameRepository.findById(GAME_ID)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> onlineGameService.getGame(GAME_ID, WHITE_ID))
                .isInstanceOf(AppException.class)
                .extracting("errorMessage")
                .isEqualTo(AppErrorMessage.ONLINE_GAME_NOT_FOUND);
    }

    private OnlineGame newGame(boolean clockEnabled, Integer initialSeconds, Integer incrementSeconds) {
        return OnlineGame.builder()
                .withSource(GameSource.INVITE)
                .withWhiteAccountId(WHITE_ID)
                .withBlackAccountId(BLACK_ID)
                .withVariantCode("STANDARD")
                .withModeCode("CLASSICAL")
                .withRated(clockEnabled)
                .withClockEnabled(clockEnabled)
                .withInitialSeconds(initialSeconds)
                .withIncrementSeconds(incrementSeconds)
                .withCreatedAt(CLAIMED_AT)
                .build();
    }

    private OnlineGameEntity storedGame() {
        return OnlineGameEntity.builder()
                .withId(GAME_ID)
                .withSource("INVITE")
                .withWhiteAccountId(WHITE_ID)
                .withBlackAccountId(BLACK_ID)
                .withStatus("PENDING")
                .withCreatedAt(CLAIMED_AT)
                .withReadyDeadlineAt(CLAIMED_AT.plusSeconds(180))
                .withInitialFen(START_FEN)
                .withCurrentFen(START_FEN)
                .build();
    }
}
