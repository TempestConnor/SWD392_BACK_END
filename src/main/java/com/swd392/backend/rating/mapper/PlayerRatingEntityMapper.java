package com.swd392.backend.rating.mapper;

import com.swd392.backend.rating.entity.PlayerRatingEntity;
import com.swd392.backend.rating.model.PlayerRating;
import org.mapstruct.Builder;
import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface PlayerRatingEntityMapper {

    @Mapping(source = "accountId", target = "id.accountId")
    @Mapping(source = "modeCode", target = "id.modeCode")
    PlayerRatingEntity toEntity(PlayerRating rating);

    @InheritInverseConfiguration
    PlayerRating toModel(PlayerRatingEntity entity);
}
