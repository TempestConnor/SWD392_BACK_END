package com.swd392.backend.invitation.mapper;

import com.swd392.backend.invitation.dto.CreateInvitationDTO;
import com.swd392.backend.invitation.dto.CreatedInvitationDTO;
import com.swd392.backend.invitation.dto.InvitationDTO;
import com.swd392.backend.invitation.model.CreatedInvitation;
import com.swd392.backend.invitation.model.Invitation;
import com.swd392.backend.invitation.model.InvitationRequest;
import com.swd392.backend.onlinegame.dto.TimeControlDTO;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface InvitationMapper {

    int SECONDS_PER_MINUTE = 60;

    InvitationRequest toRequest(CreateInvitationDTO dto);

    @Mapping(source = "modeCode", target = "mode")
    @Mapping(source = "variantCode", target = "variant")
    @Mapping(source = ".", target = "clock", qualifiedByName = "clock")
    InvitationDTO toDto(Invitation invitation);

    CreatedInvitationDTO toDto(CreatedInvitation createdInvitation);

    @Named("clock")
    default TimeControlDTO toClockDto(Invitation invitation) {
        Integer initialMinutes =
                invitation.getInitialSeconds() == null ? null : invitation.getInitialSeconds() / SECONDS_PER_MINUTE;
        TimeControlDTO clock =
                new TimeControlDTO(invitation.isClockEnabled(), initialMinutes, invitation.getIncrementSeconds());

        return clock;
    }
}
