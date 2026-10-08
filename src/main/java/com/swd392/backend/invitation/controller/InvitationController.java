package com.swd392.backend.invitation.controller;

import com.swd392.backend.invitation.dto.ClaimInvitationDTO;
import com.swd392.backend.invitation.dto.CreateInvitationDTO;
import com.swd392.backend.invitation.dto.CreatedInvitationDTO;
import com.swd392.backend.invitation.dto.InvitationDTO;
import com.swd392.backend.invitation.mapper.InvitationMapper;
import com.swd392.backend.invitation.service.InvitationService;
import com.swd392.backend.onlinegame.dto.OnlineGameDTO;
import com.swd392.backend.onlinegame.mapper.OnlineGameMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/invitations")
@RequiredArgsConstructor
public class InvitationController {

    private final InvitationService invitationService;
    private final InvitationMapper invitationMapper;
    private final OnlineGameMapper onlineGameMapper;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedInvitationDTO createInvitation(
            @AuthenticationPrincipal Jwt jwt, @RequestBody @Valid CreateInvitationDTO dto) {
        var hostAccountId = Long.valueOf(jwt.getSubject());
        var request = invitationMapper.toRequest(dto);
        var created = invitationService.create(hostAccountId, request);
        var response = invitationMapper.toDto(created);

        return response;
    }

    @GetMapping("/{invitationId}")
    public InvitationDTO getInvitation(@AuthenticationPrincipal Jwt jwt, @PathVariable Long invitationId) {
        var hostAccountId = Long.valueOf(jwt.getSubject());
        var invitation = invitationService.getInvitation(invitationId, hostAccountId);
        var response = invitationMapper.toDto(invitation);

        return response;
    }

    @PostMapping("/{invitationId}/cancel")
    public InvitationDTO cancelInvitation(@AuthenticationPrincipal Jwt jwt, @PathVariable Long invitationId) {
        var hostAccountId = Long.valueOf(jwt.getSubject());
        var invitation = invitationService.cancel(invitationId, hostAccountId);
        var response = invitationMapper.toDto(invitation);

        return response;
    }

    @PostMapping("/claim")
    public OnlineGameDTO claimInvitation(@AuthenticationPrincipal Jwt jwt, @RequestBody @Valid ClaimInvitationDTO dto) {
        var joinerAccountId = Long.valueOf(jwt.getSubject());
        var game = invitationService.claim(dto.token(), joinerAccountId);
        var response = onlineGameMapper.toDto(game);

        return response;
    }
}
