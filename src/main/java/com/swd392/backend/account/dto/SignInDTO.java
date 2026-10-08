package com.swd392.backend.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

public record SignInDTO(
        @NotBlank(message = "is required") String email, @NotEmpty(message = "is required") String password) {

    public SignInDTO {
        email = email == null ? null : email.strip();
    }
}
