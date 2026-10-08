package com.swd392.backend.invitation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(setterPrefix = "with")
public class InvitationRequest {
    private HostColor hostColor;
    private Boolean clockEnabled;
    private Integer initialMinutes;
    private Integer incrementSeconds;
}
