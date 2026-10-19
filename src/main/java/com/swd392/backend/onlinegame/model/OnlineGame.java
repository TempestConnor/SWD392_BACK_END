package com.swd392.backend.onlinegame.model;

import com.swd392.backend.chess.model.ChessColor;
import com.swd392.backend.chess.model.GameResult;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(setterPrefix = "with")
public class OnlineGame {
    private Long id;
    private GameSource source;
    private Long whiteAccountId;
    private String whiteDisplayName;
    private Long blackAccountId;
    private String blackDisplayName;
    private ChessColor viewerColor;
    private String variantCode;
    private String modeCode;
    private boolean rated;
    private boolean clockEnabled;
    private Integer initialSeconds;
    private Integer incrementSeconds;
    private Instant createdAt;
    private Instant readyDeadlineAt;
    private Instant whiteReadyAt;
    private Instant blackReadyAt;
    private Instant startedAt;
    private Instant endedAt;
    private OnlineGameStatus status;
    private GameResult outcome;
    private TerminationReason terminationReason;
    private String initialFen;
    private String currentFen;
    private int lastPly;
    private Long whiteRemainingMs;
    private Long blackRemainingMs;
    private Instant turnStartedAt;
    private Instant whiteDisconnectedAt;
    private Instant blackDisconnectedAt;
    private Instant whiteLastSeenAt;
    private Instant blackLastSeenAt;
    private boolean whiteAbandonVoted;
    private boolean blackAbandonVoted;
}
