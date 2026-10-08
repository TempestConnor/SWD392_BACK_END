package com.swd392.backend.invitation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ClaimInvitationDTO(
        @NotNull(message = "is required")
                // 32 random bytes encoded as unpadded base64url.
                @Pattern(regexp = "[A-Za-z0-9_-]{43}", message = "must be a valid invitation token")
                String token) {}
