package com.swd392.backend.account.model;

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
public class AccountRegistration {
    private String email;
    private String password;
    private String displayName;
    private SkillLevel signupSkill;
}
