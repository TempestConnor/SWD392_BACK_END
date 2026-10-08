package com.swd392.backend.onlinegame.mapper;

import com.swd392.backend.onlinegame.dto.GamePlayerDTO;
import com.swd392.backend.onlinegame.dto.OnlineGameDTO;
import com.swd392.backend.onlinegame.dto.TimeControlDTO;
import com.swd392.backend.onlinegame.model.OnlineGame;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface OnlineGameMapper {

    int SECONDS_PER_MINUTE = 60;

    @Mapping(source = "modeCode", target = "mode")
    @Mapping(source = "variantCode", target = "variant")
    @Mapping(source = ".", target = "clock", qualifiedByName = "clock")
    @Mapping(source = ".", target = "white", qualifiedByName = "white")
    @Mapping(source = ".", target = "black", qualifiedByName = "black")
    OnlineGameDTO toDto(OnlineGame game);

    @Named("clock")
    default TimeControlDTO toClockDto(OnlineGame game) {
        Integer initialMinutes =
                game.getInitialSeconds() == null ? null : game.getInitialSeconds() / SECONDS_PER_MINUTE;
        TimeControlDTO clock = new TimeControlDTO(game.isClockEnabled(), initialMinutes, game.getIncrementSeconds());

        return clock;
    }

    @Named("white")
    default GamePlayerDTO toWhiteDto(OnlineGame game) {
        GamePlayerDTO white =
                new GamePlayerDTO(game.getWhiteAccountId(), game.getWhiteDisplayName(), game.getWhiteReadyAt() != null);

        return white;
    }

    @Named("black")
    default GamePlayerDTO toBlackDto(OnlineGame game) {
        GamePlayerDTO black =
                new GamePlayerDTO(game.getBlackAccountId(), game.getBlackDisplayName(), game.getBlackReadyAt() != null);

        return black;
    }
}
