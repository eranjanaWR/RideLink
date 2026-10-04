package com.ridelink.farepayment.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String INTERNAL_SERVICE_HEADER = "X-Internal-Service-Key";

    private final JwtTokenValidator tokenValidator;
    private final InternalServiceAuthenticator internalServiceAuthenticator;
    private final SecurityErrorHandler errorHandler;

    public JwtAuthenticationFilter(
            JwtTokenValidator tokenValidator,
            InternalServiceAuthenticator internalServiceAuthenticator,
            SecurityErrorHandler errorHandler
    ) {
        this.tokenValidator = tokenValidator;
        this.internalServiceAuthenticator = internalServiceAuthenticator;
        this.errorHandler = errorHandler;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = requestPath(request);
        return path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String authorization = request.getHeader("Authorization");
        if (authorization != null) {
            authenticateJwt(request, response, filterChain, authorization);
            return;
        }

        String internalKey = request.getHeader(INTERNAL_SERVICE_HEADER);
        if (internalKey != null && supportsInternalAuthentication(requestPath(request))) {
            if (!internalServiceAuthenticator.isValid(internalKey)) {
                errorHandler.writeUnauthorized(request, response);
                return;
            }
            setAuthentication(
                    request,
                    new InternalServicePrincipal("ride-management-service"),
                    "ROLE_INTERNAL_SERVICE");
        }

        filterChain.doFilter(request, response);
    }

    private void authenticateJwt(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain,
            String authorization
    ) throws IOException, ServletException {
        if (!authorization.startsWith("Bearer ")) {
            errorHandler.writeUnauthorized(request, response);
            return;
        }

        String token = authorization.substring(7).trim();
        try {
            if (token.isEmpty()) {
                throw new JwtException("Token is empty");
            }
            AuthenticatedUser user = tokenValidator.validate(token);
            if (user.status() != AccountStatus.ACTIVE) {
                errorHandler.writeForbidden(request, response);
                return;
            }
            setAuthentication(request, user, "ROLE_" + user.role().name());
            filterChain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException exception) {
            SecurityContextHolder.clearContext();
            errorHandler.writeUnauthorized(request, response);
        }
    }

    private void setAuthentication(HttpServletRequest request, Object principal, String authority) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority(authority)));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private boolean supportsInternalAuthentication(String path) {
        return path.equals("/api/fares/final")
                || path.startsWith("/api/fares/final/")
                || path.equals("/api/payments")
                || path.startsWith("/api/payments/");
    }

    private String requestPath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return contextPath.isEmpty() ? requestUri : requestUri.substring(contextPath.length());
    }
}
