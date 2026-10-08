package com.swd392.backend.account.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateAccountDTO(
        @NotBlank(message = "is required")
                @Email(message = "must be a valid email address")
                @Size(min = 4, max = 320, message = "must be between 4 and 320 characters")
                String email,
        @NotNull(message = "is required") @Size(min = 8, max = 72, message = "must be between 8 and 72 characters")
                String password,
        @NotBlank(message = "is required")
                @Size(min = 3, max = 40, message = "must be between 3 and 40 characters")
                // Words of non-whitespace separated by single spaces; (?U) treats Unicode spaces as whitespace.
                @Pattern(
                        regexp = "(?U)\\S+( \\S+)*",
                        message = "must use single spaces between words and no other whitespace")
                String displayName,
        @NotNull(message = "is required")
                @Pattern(
                        regexp = "BEGINNER|INTERMEDIATE|ADVANCED|EXPERT",
                        message = "must be one of BEGINNER, INTERMEDIATE, ADVANCED, EXPERT")
                String signupSkill) {

    public CreateAccountDTO {
        email = email == null ? null : email.strip();
        displayName = displayName == null ? null : displayName.strip();
    }
}
