package com.swd392.backend.rating.mapper;

import com.swd392.backend.rating.entity.RatingEventEntity;
import com.swd392.backend.rating.model.RatingEvent;
import java.util.List;
import org.mapstruct.Builder;
import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface RatingEventEntityMapper {

    @Mapping(source = "gameId", target = "id.gameId")
    @Mapping(source = "accountId", target = "id.accountId")
    RatingEventEntity toEntity(RatingEvent event);

    @InheritInverseConfiguration
    RatingEvent toModel(RatingEventEntity entity);

    List<RatingEvent> toModels(List<RatingEventEntity> entities);
}
