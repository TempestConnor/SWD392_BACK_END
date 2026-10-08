package com.swd392.backend.localgame.service;

import com.swd392.backend.chess.model.ChessPosition;
import com.swd392.backend.chess.service.ChessRulesService;
import com.swd392.backend.common.exception.AppErrorMessage;
import com.swd392.backend.common.exception.AppException;
import com.swd392.backend.localgame.model.ClockSettings;
import com.swd392.backend.localgame.model.LocalGameStart;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LocalGameServiceImpl implements LocalGameService {

    private static final int DEFAULT_INITIAL_MINUTES = 30;
    private static final int DEFAULT_INCREMENT_SECONDS = 10;

    private final ChessRulesService chessRulesService;

    @Override
    public LocalGameStart start(ClockSettings requestedClock) {
        ClockSettings clock = resolveClock(requestedClock);
        ChessPosition initialPosition = chessRulesService.replay(List.of());
        LocalGameStart start = LocalGameStart.builder()
                .withClock(clock)
                .withPosition(initialPosition)
                .build();

        return start;
    }

    @Override
    public ChessPosition replay(List<String> moves) {
        return chessRulesService.replay(moves);
    }

    private ClockSettings resolveClock(ClockSettings requested) {
        boolean disabled = Boolean.FALSE.equals(requested.getEnabled());
        if (!disabled) {
            return ClockSettings.builder()
                    .withEnabled(true)
                    .withInitialMinutes(valueOrDefault(requested.getInitialMinutes(), DEFAULT_INITIAL_MINUTES))
                    .withIncrementSeconds(valueOrDefault(requested.getIncrementSeconds(), DEFAULT_INCREMENT_SECONDS))
                    .build();
        }
        boolean hasTiming = requested.getInitialMinutes() != null || requested.getIncrementSeconds() != null;
        if (hasTiming) {
            throw new AppException(AppErrorMessage.DISABLED_CLOCK_HAS_TIMING);
        }
        ClockSettings disabledClock = ClockSettings.builder().withEnabled(false).build();

        return disabledClock;
    }

    private int valueOrDefault(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }
}
