package com.swd392.backend.onlinegame.mapper;

import com.swd392.backend.onlinegame.entity.GameMoveEntity;
import com.swd392.backend.onlinegame.model.GameMove;
import java.util.List;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface GameMoveEntityMapper {

    @Mapping(source = "gameId", target = "id.gameId")
    @Mapping(source = "ply", target = "id.ply")
    GameMoveEntity toEntity(GameMove move);

    @Mapping(source = "id.gameId", target = "gameId")
    @Mapping(source = "id.ply", target = "ply")
    GameMove toModel(GameMoveEntity entity);

    List<GameMove> toModels(List<GameMoveEntity> entities);
}
