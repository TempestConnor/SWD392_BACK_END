package com.swd392.backend.security.service;

import com.swd392.backend.security.model.AccessToken;
import java.util.Collection;

public interface TokenService {

    AccessToken issueAccessToken(String subject, Collection<String> roles);
}
