package com.swd392.backend.cpugame.dto;

public record CpuGameStateDTO(
        String difficulty,
        String playerColor,
        String cpuMove,
        String cpuMoveSan,
        String fen,
        String sideToMove,
        int ply,
        String status,
        String result) {}
