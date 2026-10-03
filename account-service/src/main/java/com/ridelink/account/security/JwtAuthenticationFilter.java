package com.ridelink.account.security;

import java.io.IOException;
import java.util.List;

import com.ridelink.account.model.AccountRole;
import com.ridelink.account.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final SecurityErrorHandler errorHandler;

    public JwtAuthenticationFilter(JwtService jwtService, SecurityErrorHandler errorHandler) {
        this.jwtService = jwtService;
        this.errorHandler = errorHandler;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(request.getContextPath() + "/api/accounts/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            reject(request, response);
            return;
        }

        String token = authorization.substring(7).trim();
        String accountId;
        AccountRole role;
        try {
            if (token.isEmpty() || !jwtService.isTokenValid(token)) {
                reject(request, response);
                return;
            }
            accountId = jwtService.extractAccountId(token);
            role = jwtService.extractRole(token);
            if (accountId == null || accountId.isBlank() || role == null) {
                reject(request, response);
                return;
            }
        } catch (RuntimeException exception) {
            reject(request, response);
            return;
        }
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                accountId, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
        SecurityContextHolder.clearContext();
        errorHandler.commence(request, response, new BadCredentialsException("Invalid token"));
    }
}
