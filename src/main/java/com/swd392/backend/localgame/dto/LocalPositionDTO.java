package com.swd392.backend.localgame.dto;

import java.util.List;

public record LocalPositionDTO(
        String fen,
        String sideToMove,
        int ply,
        String lastMoveSan,
        String status,
        String result,
        List<String> legalMoves) {}
