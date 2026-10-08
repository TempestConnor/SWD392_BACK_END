package com.swd392.backend.account.dto;

import java.util.List;
import java.util.Set;

public record AccountDTO(
        Long id,
        String email,
        String displayName,
        String signupSkill,
        Set<String> roles,
        List<PlayerRatingDTO> ratings) {}
