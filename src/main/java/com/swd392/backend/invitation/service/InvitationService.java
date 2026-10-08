package com.swd392.backend.invitation.service;

import com.swd392.backend.invitation.model.CreatedInvitation;
import com.swd392.backend.invitation.model.Invitation;
import com.swd392.backend.invitation.model.InvitationRequest;
import com.swd392.backend.onlinegame.model.OnlineGame;

public interface InvitationService {

    CreatedInvitation create(Long hostAccountId, InvitationRequest request);

    Invitation getInvitation(Long invitationId, Long hostAccountId);

    Invitation cancel(Long invitationId, Long hostAccountId);

    OnlineGame claim(String token, Long joinerAccountId);
}
