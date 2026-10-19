package com.swd392.backend.onlinegame.controller;

import com.swd392.backend.common.dto.PageResponse;
import com.swd392.backend.onlinegame.dto.MatchHistoryDetailDTO;
import com.swd392.backend.onlinegame.dto.MatchHistoryEntryDTO;
import com.swd392.backend.onlinegame.mapper.MatchHistoryMapper;
import com.swd392.backend.onlinegame.service.MatchHistoryService;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/match-history")
@RequiredArgsConstructor
public class MatchHistoryController {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final MatchHistoryService matchHistoryService;
    private final MatchHistoryMapper matchHistoryMapper;

    @GetMapping
    public PageResponse<MatchHistoryEntryDTO> searchHistory(
            @AuthenticationPrincipal Jwt jwt,
            @ParameterObject @PageableDefault(size = DEFAULT_PAGE_SIZE) Pageable pageable) {
        var accountId = Long.valueOf(jwt.getSubject());
        var history = matchHistoryService.searchHistory(accountId, pageable);
        var entries = history.map(matchHistoryMapper::toEntryDto);
        var response = PageResponse.from(entries);

        return response;
    }

    @GetMapping("/{gameId}")
    public MatchHistoryDetailDTO getHistoryGame(@AuthenticationPrincipal Jwt jwt, @PathVariable Long gameId) {
        var accountId = Long.valueOf(jwt.getSubject());
        var history = matchHistoryService.getHistoryGame(gameId, accountId);
        var response = matchHistoryMapper.toDetailDto(history);

        return response;
    }
}
