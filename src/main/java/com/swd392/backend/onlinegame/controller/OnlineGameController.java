package com.swd392.backend.onlinegame.controller;

import com.swd392.backend.onlinegame.dto.OnlineGameDTO;
import com.swd392.backend.onlinegame.mapper.OnlineGameMapper;
import com.swd392.backend.onlinegame.service.OnlineGameService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
}
