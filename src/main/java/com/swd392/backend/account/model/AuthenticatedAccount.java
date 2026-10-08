package com.swd392.backend.account.model;

import java.time.Instant;
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
public class AuthenticatedAccount {
    private Account account;
    private String accessToken;
    private Instant expiresAt;
}
