package com.swd392.backend.matchmaking.controller;

import com.swd392.backend.matchmaking.dto.JoinQueueDTO;
import com.swd392.backend.matchmaking.dto.QueueRequestDTO;
import com.swd392.backend.matchmaking.mapper.MatchmakingMapper;
import com.swd392.backend.matchmaking.service.MatchmakingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/matchmaking")
@RequiredArgsConstructor
public class MatchmakingController {

    private final MatchmakingService matchmakingService;
    private final MatchmakingMapper matchmakingMapper;

    @PostMapping("/join")
    public QueueRequestDTO join(@AuthenticationPrincipal Jwt jwt, @RequestBody @Valid JoinQueueDTO dto) {
        var accountId = Long.valueOf(jwt.getSubject());
        var request = matchmakingMapper.toJoinRequest(dto);
        var queueRequest = matchmakingService.join(accountId, request);
        var response = matchmakingMapper.toDto(queueRequest);

        return response;
    }

    @PostMapping("/heartbeat")
    public QueueRequestDTO heartbeat(@AuthenticationPrincipal Jwt jwt) {
        var accountId = Long.valueOf(jwt.getSubject());
        var queueRequest = matchmakingService.heartbeat(accountId);
        var response = matchmakingMapper.toDto(queueRequest);

        return response;
    }

    @PostMapping("/cancel")
    public QueueRequestDTO cancel(@AuthenticationPrincipal Jwt jwt) {
        var accountId = Long.valueOf(jwt.getSubject());
        var queueRequest = matchmakingService.cancel(accountId);
        var response = matchmakingMapper.toDto(queueRequest);

        return response;
    }
}
