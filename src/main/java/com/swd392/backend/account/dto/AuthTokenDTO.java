package com.swd392.backend.account.dto;

import java.time.Instant;

public record AuthTokenDTO(String accessToken, String tokenType, Instant expiresAt, AccountDTO account) {}
