package com.swd392.backend.account.service;

import com.swd392.backend.account.entity.AccountEntity;
import com.swd392.backend.account.mapper.AccountEntityMapper;
import com.swd392.backend.account.model.Account;
import com.swd392.backend.account.model.AccountRegistration;
import com.swd392.backend.account.model.AuthenticatedAccount;
import com.swd392.backend.account.model.Credentials;
import com.swd392.backend.account.model.Role;
import com.swd392.backend.account.repository.AccountRepository;
import com.swd392.backend.common.exception.AppErrorMessage;
import com.swd392.backend.common.exception.AppException;
import com.swd392.backend.rating.model.PlayerRating;
import com.swd392.backend.rating.service.RatingService;
import com.swd392.backend.security.model.AccessToken;
import com.swd392.backend.security.service.TokenService;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private static final int MAX_PASSWORD_BYTES = 72;
    private static final String EMAIL_UNIQUE_CONSTRAINT = "UQ_Account_email";
    private static final String DISPLAY_NAME_UNIQUE_CONSTRAINT = "UQ_Account_name";

    private final AccountRepository accountRepository;
    private final AccountEntityMapper accountEntityMapper;
    private final PasswordEncoder passwordEncoder;
    private final RatingService ratingService;
    private final TokenService tokenService;

    @Override
    public AuthenticatedAccount register(AccountRegistration registration) {
        if (registration.getPassword().getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new AppException(AppErrorMessage.PASSWORD_TOO_LONG);
        }
        if (accountRepository.existsByEmail(registration.getEmail())) {
            throw new AppException(AppErrorMessage.EMAIL_ALREADY_REGISTERED);
        }
        if (accountRepository.existsByDisplayName(registration.getDisplayName())) {
            throw new AppException(AppErrorMessage.DISPLAY_NAME_TAKEN);
        }

        String passwordHash = passwordEncoder.encode(registration.getPassword());
        AccountEntity entity = accountEntityMapper.toEntity(registration, passwordHash);
        AccountEntity saved = insertAccount(entity);
        List<PlayerRating> ratings = ratingService.initializeRatings(
                saved.getId(), registration.getSignupSkill().getStartingRating());

        Account account = accountEntityMapper.toModel(saved);
        account.setRatings(ratings);
        log.info("[ACCOUNT] - ACTION: register: accountId: {}", account.getId());

        return authenticate(account);
    }

    @Override
    public AuthenticatedAccount signIn(Credentials credentials) {
        AccountEntity entity = accountRepository
                .findByEmail(credentials.getEmail())
                .orElseThrow(() -> new AppException(AppErrorMessage.INVALID_CREDENTIALS));
        if (!passwordEncoder.matches(credentials.getPassword(), entity.getPasswordHash())) {
            throw new AppException(AppErrorMessage.INVALID_CREDENTIALS);
        }

        Account account = toAccountWithRatings(entity);
        log.info("[ACCOUNT] - ACTION: signIn: accountId: {}", account.getId());

        return authenticate(account);
    }

    @Override
    public Account getCurrentAccount(Long accountId) {
        AccountEntity entity = accountRepository
                .findById(accountId)
                .orElseThrow(() -> new AppException(AppErrorMessage.CURRENT_ACCOUNT_NOT_FOUND));

        return toAccountWithRatings(entity);
    }

    @Override
    public Map<Long, String> getDisplayNames(Collection<Long> accountIds) {
        return accountRepository.findAllById(accountIds).stream()
                .collect(Collectors.toMap(AccountEntity::getId, AccountEntity::getDisplayName));
    }

    private AccountEntity insertAccount(AccountEntity entity) {
        try {
            return accountRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException exception) {
            String cause = String.valueOf(
                    NestedExceptionUtils.getMostSpecificCause(exception).getMessage());
            if (cause.contains(EMAIL_UNIQUE_CONSTRAINT)) {
                throw new AppException(AppErrorMessage.EMAIL_ALREADY_REGISTERED);
            }
            if (cause.contains(DISPLAY_NAME_UNIQUE_CONSTRAINT)) {
                throw new AppException(AppErrorMessage.DISPLAY_NAME_TAKEN);
            }
            throw exception;
        }
    }

    private Account toAccountWithRatings(AccountEntity entity) {
        Account account = accountEntityMapper.toModel(entity);
        account.setRatings(ratingService.getRatings(account.getId()));

        return account;
    }

    private AuthenticatedAccount authenticate(Account account) {
        List<String> roleNames = account.getRoles().stream().map(Role::name).toList();
        AccessToken token = tokenService.issueAccessToken(String.valueOf(account.getId()), roleNames);
        AuthenticatedAccount authenticated = AuthenticatedAccount.builder()
                .withAccount(account)
                .withAccessToken(token.getValue())
                .withExpiresAt(token.getExpiresAt())
                .build();

        return authenticated;
    }
}
