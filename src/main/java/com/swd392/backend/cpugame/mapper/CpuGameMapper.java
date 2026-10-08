package com.swd392.backend.cpugame.mapper;

import com.swd392.backend.cpugame.dto.CpuGameStateDTO;
import com.swd392.backend.cpugame.dto.CpuMoveRequestDTO;
import com.swd392.backend.cpugame.dto.StartCpuGameDTO;
import com.swd392.backend.cpugame.model.CpuGameStart;
import com.swd392.backend.cpugame.model.CpuGameState;
import com.swd392.backend.cpugame.model.CpuTurn;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface CpuGameMapper {

    CpuGameStart toStart(StartCpuGameDTO dto);

    CpuTurn toTurn(CpuMoveRequestDTO dto);

    @Mapping(source = "position.fen", target = "fen")
    @Mapping(source = "position.sideToMove", target = "sideToMove")
    @Mapping(source = "position.ply", target = "ply")
    @Mapping(source = "position.status", target = "status")
    @Mapping(source = "position.result", target = "result")
    CpuGameStateDTO toDto(CpuGameState state);
}
