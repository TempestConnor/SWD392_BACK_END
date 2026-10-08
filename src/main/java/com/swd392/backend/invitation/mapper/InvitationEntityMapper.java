package com.swd392.backend.invitation.mapper;

import com.swd392.backend.invitation.entity.InvitationEntity;
import com.swd392.backend.invitation.model.Invitation;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface InvitationEntityMapper {

    @Mapping(source = "tokenHash", target = "tokenHash")
    InvitationEntity toEntity(Invitation invitation, byte[] tokenHash);

    @Mapping(target = "rated", ignore = true)
    @Mapping(target = "status", ignore = true)
    Invitation toModel(InvitationEntity entity);
}
