package com.swd392.backend.onlinegame.controller;

import com.swd392.backend.onlinegame.dto.GameMoveDTO;
import com.swd392.backend.onlinegame.dto.MakeMoveDTO;
import com.swd392.backend.onlinegame.dto.OnlineGameDTO;
import com.swd392.backend.onlinegame.mapper.OnlineGameMapper;
import com.swd392.backend.onlinegame.service.OnlineGameService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/online-games")
@RequiredArgsConstructor
public class OnlineGameController {

    private final OnlineGameService onlineGameService;
    private final OnlineGameMapper onlineGameMapper;

    @GetMapping("/{gameId}")
    public OnlineGameDTO getGame(@AuthenticationPrincipal Jwt jwt, @PathVariable Long gameId) {
        var accountId = Long.valueOf(jwt.getSubject());
        var game = onlineGameService.getGame(gameId, accountId);
        var response = onlineGameMapper.toDto(game);

        return response;
    }

    @PostMapping("/{gameId}/ready")
    public OnlineGameDTO markReady(@AuthenticationPrincipal Jwt jwt, @PathVariable Long gameId) {
        var accountId = Long.valueOf(jwt.getSubject());
        var game = onlineGameService.markReady(gameId, accountId);
        var response = onlineGameMapper.toDto(game);

        return response;
    }

    @PostMapping("/{gameId}/cancel")
    public OnlineGameDTO cancelGame(@AuthenticationPrincipal Jwt jwt, @PathVariable Long gameId) {
        var accountId = Long.valueOf(jwt.getSubject());
        var game = onlineGameService.cancelPendingGame(gameId, accountId);
        var response = onlineGameMapper.toDto(game);

        return response;
    }

    @PostMapping("/{gameId}/heartbeat")
    public OnlineGameDTO recordHeartbeat(@AuthenticationPrincipal Jwt jwt, @PathVariable Long gameId) {
        var accountId = Long.valueOf(jwt.getSubject());
        var game = onlineGameService.recordHeartbeat(gameId, accountId);
        var response = onlineGameMapper.toDto(game);

        return response;
    }

    @PostMapping("/{gameId}/moves")
    public OnlineGameDTO makeMove(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long gameId, @RequestBody @Valid MakeMoveDTO dto) {
        var accountId = Long.valueOf(jwt.getSubject());
        var submission = onlineGameMapper.toModel(dto);
        var game = onlineGameService.makeMove(gameId, accountId, submission);
        var response = onlineGameMapper.toDto(game);

        return response;
    }

    @PostMapping("/{gameId}/resign")
    public OnlineGameDTO resign(@AuthenticationPrincipal Jwt jwt, @PathVariable Long gameId) {
        var accountId = Long.valueOf(jwt.getSubject());
        var game = onlineGameService.resign(gameId, accountId);
        var response = onlineGameMapper.toDto(game);

        return response;
    }

    @PostMapping("/{gameId}/abandon")
    public OnlineGameDTO voteToAbandon(@AuthenticationPrincipal Jwt jwt, @PathVariable Long gameId) {
        var accountId = Long.valueOf(jwt.getSubject());
        var game = onlineGameService.voteToAbandon(gameId, accountId);
        var response = onlineGameMapper.toDto(game);

        return response;
    }

    @GetMapping("/{gameId}/moves")
    public List<GameMoveDTO> getMoves(@AuthenticationPrincipal Jwt jwt, @PathVariable Long gameId) {
        var accountId = Long.valueOf(jwt.getSubject());
        var moves = onlineGameService.getMoves(gameId, accountId);
        var response = onlineGameMapper.toMoveDtos(moves);

        return response;
    }
}
