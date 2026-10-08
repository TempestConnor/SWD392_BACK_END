package com.swd392.backend.localgame.controller;

import com.swd392.backend.localgame.dto.LocalGameDTO;
import com.swd392.backend.localgame.dto.LocalMovesDTO;
import com.swd392.backend.localgame.dto.LocalPositionDTO;
import com.swd392.backend.localgame.dto.StartLocalGameDTO;
import com.swd392.backend.localgame.mapper.LocalGameMapper;
import com.swd392.backend.localgame.service.LocalGameService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/local-games")
@RequiredArgsConstructor
public class LocalGameController {

    private final LocalGameService localGameService;
    private final LocalGameMapper localGameMapper;

    @PostMapping
    public LocalGameDTO startGame(@RequestBody @Valid StartLocalGameDTO dto) {
        var requestedClock = localGameMapper.toClockSettings(dto);
        var start = localGameService.start(requestedClock);
        var response = localGameMapper.toDto(start);

        return response;
    }

    @PostMapping("/state")
    public LocalPositionDTO getState(@RequestBody @Valid LocalMovesDTO dto) {
        var position = localGameService.replay(dto.moves());
        var response = localGameMapper.toDto(position);

        return response;
    }
}
