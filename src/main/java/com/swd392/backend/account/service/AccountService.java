package com.swd392.backend.account.service;

import com.swd392.backend.account.model.Account;
import com.swd392.backend.account.model.AccountRegistration;
import com.swd392.backend.account.model.AuthenticatedAccount;
import com.swd392.backend.account.model.Credentials;
import java.util.Collection;
import java.util.Map;

public interface AccountService {

    AuthenticatedAccount register(AccountRegistration registration);

    AuthenticatedAccount signIn(Credentials credentials);

    Account getCurrentAccount(Long accountId);

    Map<Long, String> getDisplayNames(Collection<Long> accountIds);
}
