package com.swd392.backend.account.mapper;

import com.swd392.backend.account.dto.AccountDTO;
import com.swd392.backend.account.dto.AuthTokenDTO;
import com.swd392.backend.account.dto.CreateAccountDTO;
import com.swd392.backend.account.dto.PlayerRatingDTO;
import com.swd392.backend.account.dto.SignInDTO;
import com.swd392.backend.account.model.Account;
import com.swd392.backend.account.model.AccountRegistration;
import com.swd392.backend.account.model.AuthenticatedAccount;
import com.swd392.backend.account.model.Credentials;
import com.swd392.backend.rating.model.PlayerRating;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface AccountMapper {

    String BEARER_TOKEN_TYPE = "Bearer";

    AccountRegistration toRegistration(CreateAccountDTO dto);

    Credentials toCredentials(SignInDTO dto);

    AccountDTO toDto(Account account);

    @Mapping(target = "tokenType", constant = BEARER_TOKEN_TYPE)
    AuthTokenDTO toDto(AuthenticatedAccount authenticatedAccount);

    @Mapping(source = "modeCode", target = "mode")
    PlayerRatingDTO toDto(PlayerRating rating);
}
