package com.swd392.backend.localgame.mapper;

import com.swd392.backend.chess.model.ChessPosition;
import com.swd392.backend.localgame.dto.LocalGameDTO;
import com.swd392.backend.localgame.dto.LocalPositionDTO;
import com.swd392.backend.localgame.dto.StartLocalGameDTO;
import com.swd392.backend.localgame.model.ClockSettings;
import com.swd392.backend.localgame.model.LocalGameStart;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface LocalGameMapper {

    @Mapping(source = "clockEnabled", target = "enabled")
    ClockSettings toClockSettings(StartLocalGameDTO dto);

    LocalGameDTO toDto(LocalGameStart start);

    LocalPositionDTO toDto(ChessPosition position);
}
