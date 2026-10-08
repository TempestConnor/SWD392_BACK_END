package com.swd392.backend.account.service;

import com.swd392.backend.account.model.Account;
import com.swd392.backend.account.model.AccountRegistration;
import com.swd392.backend.account.model.AuthenticatedAccount;
import com.swd392.backend.account.model.Credentials;

public interface AccountService {

    AuthenticatedAccount register(AccountRegistration registration);

    AuthenticatedAccount signIn(Credentials credentials);

    Account getCurrentAccount(Long accountId);
}
