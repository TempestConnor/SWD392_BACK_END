package com.swd392.backend.onlinegame.mapper;

import com.swd392.backend.chess.model.ChessColor;
import com.swd392.backend.onlinegame.dto.HistoryPlayerDTO;
import com.swd392.backend.onlinegame.dto.MatchHistoryDetailDTO;
import com.swd392.backend.onlinegame.dto.MatchHistoryEntryDTO;
import com.swd392.backend.onlinegame.dto.RatingChangeDTO;
import com.swd392.backend.onlinegame.model.GameHistory;
import com.swd392.backend.onlinegame.model.OnlineGame;
import com.swd392.backend.rating.model.RatingEvent;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring", uses = OnlineGameMapper.class, builder = @Builder(disableBuilder = true))
public interface MatchHistoryMapper {

    @Mapping(source = "game.id", target = "gameId")
    @Mapping(source = "game.source", target = "source")
    @Mapping(source = "game.rated", target = "rated")
    @Mapping(source = "game.modeCode", target = "mode")
    @Mapping(source = "game.variantCode", target = "variant")
    @Mapping(source = "game", target = "clock", qualifiedByName = "clock")
    @Mapping(source = "game.viewerColor", target = "viewerColor")
    @Mapping(source = "game", target = "opponent", qualifiedByName = "opponent")
    @Mapping(source = "viewerResult", target = "result")
    @Mapping(source = "game.outcome", target = "outcome")
    @Mapping(source = "game.terminationReason", target = "terminationReason")
    @Mapping(source = "game.startedAt", target = "startedAt")
    @Mapping(source = "game.endedAt", target = "endedAt")
    @Mapping(source = "game.lastPly", target = "plyCount")
    @Mapping(source = ".", target = "ratingChange", qualifiedByName = "viewerRatingChange")
    MatchHistoryEntryDTO toEntryDto(GameHistory history);

    @Mapping(source = "game.id", target = "gameId")
    @Mapping(source = "game.source", target = "source")
    @Mapping(source = "game.rated", target = "rated")
    @Mapping(source = "game.modeCode", target = "mode")
    @Mapping(source = "game.variantCode", target = "variant")
    @Mapping(source = "game", target = "clock", qualifiedByName = "clock")
    @Mapping(source = "game.viewerColor", target = "viewerColor")
    @Mapping(source = "viewerResult", target = "result")
    @Mapping(source = "game", target = "white", qualifiedByName = "historyWhite")
    @Mapping(source = "game", target = "black", qualifiedByName = "historyBlack")
    @Mapping(source = "whiteRatingEvent", target = "whiteRatingChange")
    @Mapping(source = "blackRatingEvent", target = "blackRatingChange")
    @Mapping(source = "game.outcome", target = "outcome")
    @Mapping(source = "game.terminationReason", target = "terminationReason")
    @Mapping(source = "game.startedAt", target = "startedAt")
    @Mapping(source = "game.endedAt", target = "endedAt")
    @Mapping(source = "game.initialFen", target = "initialFen")
    @Mapping(source = "game.currentFen", target = "finalFen")
    @Mapping(source = "game.lastPly", target = "plyCount")
    MatchHistoryDetailDTO toDetailDto(GameHistory history);

    default RatingChangeDTO toRatingChangeDto(RatingEvent event) {
        if (event == null) {
            return null;
        }
        RatingChangeDTO change = new RatingChangeDTO(
                event.getRatingBefore(), event.getRatingDelta(), event.getRatingAfter(), event.getKFactorUsed());

        return change;
    }

    @Named("viewerRatingChange")
    default RatingChangeDTO toViewerRatingChangeDto(GameHistory history) {
        boolean viewerWhite = history.getGame().getViewerColor() == ChessColor.WHITE;
        RatingEvent event = viewerWhite ? history.getWhiteRatingEvent() : history.getBlackRatingEvent();

        return toRatingChangeDto(event);
    }

    @Named("opponent")
    default HistoryPlayerDTO toOpponentDto(OnlineGame game) {
        boolean viewerWhite = game.getViewerColor() == ChessColor.WHITE;

        return viewerWhite ? toBlackPlayerDto(game) : toWhitePlayerDto(game);
    }

    @Named("historyWhite")
    default HistoryPlayerDTO toWhitePlayerDto(OnlineGame game) {
        HistoryPlayerDTO white = new HistoryPlayerDTO(game.getWhiteAccountId(), game.getWhiteDisplayName());

        return white;
    }

    @Named("historyBlack")
    default HistoryPlayerDTO toBlackPlayerDto(OnlineGame game) {
        HistoryPlayerDTO black = new HistoryPlayerDTO(game.getBlackAccountId(), game.getBlackDisplayName());

        return black;
    }
}
