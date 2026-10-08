package com.swd392.backend.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;

public class ProblemDetailsAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String UNAUTHENTICATED_BODY =
            """
            {"type":"about:blank","title":"Unauthorized","status":401,\
            "detail":"Authentication is required or the supplied token is invalid.",\
            "errorCode":"UNAUTHENTICATED"}""";

    private final BearerTokenAuthenticationEntryPoint bearerEntryPoint = new BearerTokenAuthenticationEntryPoint();

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        bearerEntryPoint.commence(request, response, authException);
        if (response.isCommitted()) {
            return;
        }
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(UNAUTHENTICATED_BODY);
    }
}
