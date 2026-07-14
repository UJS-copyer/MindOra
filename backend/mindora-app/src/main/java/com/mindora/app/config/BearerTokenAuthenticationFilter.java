package com.mindora.app.config;

import com.mindora.user.domain.TokenPrincipal;
import com.mindora.user.infrastructure.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class BearerTokenAuthenticationFilter extends OncePerRequestFilter {
    private final TokenService tokenService;

    public BearerTokenAuthenticationFilter(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        tokenService.verify(request.getHeader("Authorization"))
                .map(this::authentication)
                .ifPresent(authentication -> SecurityContextHolder.getContext()
                        .setAuthentication(authentication));
        filterChain.doFilter(request, response);
    }

    private UsernamePasswordAuthenticationToken authentication(TokenPrincipal principal) {
        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.roles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                        .toList());
    }
}
