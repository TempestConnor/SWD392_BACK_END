package com.swd392.backend.account.controller;

import com.swd392.backend.account.dto.AccountDTO;
import com.swd392.backend.account.dto.AuthTokenDTO;
import com.swd392.backend.account.dto.CreateAccountDTO;
import com.swd392.backend.account.dto.SignInDTO;
import com.swd392.backend.account.mapper.AccountMapper;
import com.swd392.backend.account.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final AccountMapper accountMapper;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthTokenDTO signUp(@RequestBody @Valid CreateAccountDTO dto) {
        var registration = accountMapper.toRegistration(dto);
        var authenticated = accountService.register(registration);
        var response = accountMapper.toDto(authenticated);

        return response;
    }

    @PostMapping("/signin")
    public AuthTokenDTO signIn(@RequestBody @Valid SignInDTO dto) {
        var credentials = accountMapper.toCredentials(dto);
        var authenticated = accountService.signIn(credentials);
        var response = accountMapper.toDto(authenticated);

        return response;
    }

    @GetMapping("/me")
    public AccountDTO getCurrentAccount(@AuthenticationPrincipal Jwt jwt) {
        var accountId = Long.valueOf(jwt.getSubject());
        var account = accountService.getCurrentAccount(accountId);
        var response = accountMapper.toDto(account);

        return response;
    }
}
