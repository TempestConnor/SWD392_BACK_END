package com.swd392.backend.security.service;

import com.swd392.backend.config.JwtProperties;
import com.swd392.backend.config.SecurityConfig;
import com.swd392.backend.security.model.AccessToken;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TokenServiceImpl implements TokenService {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;

    @Override
    public AccessToken issueAccessToken(String subject, Collection<String> roles) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(jwtProperties.accessTokenTtl());
        List<String> roleClaim = roles == null ? List.of() : List.copyOf(roles);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .subject(subject)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim(SecurityConfig.ROLES_CLAIM, roleClaim)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String value =
                jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        AccessToken token =
                AccessToken.builder().withValue(value).withExpiresAt(expiresAt).build();

        return token;
    }
}
