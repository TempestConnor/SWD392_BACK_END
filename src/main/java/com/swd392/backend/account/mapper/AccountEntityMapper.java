package com.swd392.backend.account.mapper;

import com.swd392.backend.account.entity.AccountEntity;
import com.swd392.backend.account.model.Account;
import com.swd392.backend.account.model.AccountRegistration;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface AccountEntityMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "roles", ignore = true)
    @Mapping(source = "registration.email", target = "email")
    @Mapping(source = "registration.displayName", target = "displayName")
    @Mapping(source = "registration.signupSkill", target = "signupSkill")
    @Mapping(source = "passwordHash", target = "passwordHash")
    AccountEntity toEntity(AccountRegistration registration, String passwordHash);

    @Mapping(target = "ratings", ignore = true)
    Account toModel(AccountEntity entity);
}
