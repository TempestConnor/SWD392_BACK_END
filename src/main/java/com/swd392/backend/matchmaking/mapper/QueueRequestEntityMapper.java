package com.swd392.backend.matchmaking.mapper;

import com.swd392.backend.matchmaking.entity.QueueRequestEntity;
import com.swd392.backend.matchmaking.model.QueueRequest;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface QueueRequestEntityMapper {

    // A new request is open: the exit columns stay null until it is matched, cancelled or timed out.
    @Mapping(target = "exitReason", ignore = true)
    @Mapping(target = "leftAt", ignore = true)
    @Mapping(target = "gameId", ignore = true)
    QueueRequestEntity toEntity(QueueRequest queueRequest);

    @Mapping(target = "status", ignore = true)
    @Mapping(target = "heartbeatIntervalSeconds", ignore = true)
    QueueRequest toModel(QueueRequestEntity entity);
}
