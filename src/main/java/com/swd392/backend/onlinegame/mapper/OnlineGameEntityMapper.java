package com.swd392.backend.onlinegame.mapper;

import com.swd392.backend.onlinegame.entity.OnlineGameEntity;
import com.swd392.backend.onlinegame.model.OnlineGame;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface OnlineGameEntityMapper {

    OnlineGameEntity toEntity(OnlineGame game);

    @Mapping(target = "whiteDisplayName", ignore = true)
    @Mapping(target = "blackDisplayName", ignore = true)
    @Mapping(target = "viewerColor", ignore = true)
    @Mapping(target = "whiteAbandonVoted", ignore = true)
    @Mapping(target = "blackAbandonVoted", ignore = true)
    OnlineGame toModel(OnlineGameEntity entity);
}
