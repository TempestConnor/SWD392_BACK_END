package com.swd392.backend.cpugame.controller;

import com.swd392.backend.cpugame.dto.CpuGameStateDTO;
import com.swd392.backend.cpugame.dto.CpuMoveRequestDTO;
import com.swd392.backend.cpugame.dto.StartCpuGameDTO;
import com.swd392.backend.cpugame.mapper.CpuGameMapper;
import com.swd392.backend.cpugame.service.CpuGameService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cpu-games")
@RequiredArgsConstructor
public class CpuGameController {

    private final CpuGameService cpuGameService;
    private final CpuGameMapper cpuGameMapper;

    @PostMapping
    public CpuGameStateDTO startGame(@RequestBody @Valid StartCpuGameDTO dto) {
        var start = cpuGameMapper.toStart(dto);
        var state = cpuGameService.start(start);
        var response = cpuGameMapper.toDto(state);

        return response;
    }

    @PostMapping("/cpu-move")
    public CpuGameStateDTO playCpuMove(@RequestBody @Valid CpuMoveRequestDTO dto) {
        var turn = cpuGameMapper.toTurn(dto);
        var state = cpuGameService.playCpuTurn(turn);
        var response = cpuGameMapper.toDto(state);

        return response;
    }
}
