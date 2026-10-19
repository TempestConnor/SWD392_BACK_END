package com.swd392.backend.matchmaking.mapper;

import com.swd392.backend.matchmaking.dto.JoinQueueDTO;
import com.swd392.backend.matchmaking.dto.QueueRequestDTO;
import com.swd392.backend.matchmaking.model.QueueJoinRequest;
import com.swd392.backend.matchmaking.model.QueueRequest;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface MatchmakingMapper {

    @Mapping(source = "mode", target = "modeCode")
    @Mapping(source = "variant", target = "variantCode")
    QueueJoinRequest toJoinRequest(JoinQueueDTO dto);

    @Mapping(source = "modeCode", target = "mode")
    @Mapping(source = "variantCode", target = "variant")
    QueueRequestDTO toDto(QueueRequest queueRequest);
}
