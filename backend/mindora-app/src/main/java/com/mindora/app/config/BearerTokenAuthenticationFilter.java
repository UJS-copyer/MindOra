package com.mindora.app.config;

import com.mindora.user.application.port.TokenService;
import com.mindora.user.domain.TokenPrincipal;
import com.mindora.user.domain.TokenVerification;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
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
        TokenVerification verification = tokenService.verify(request.getHeader("Authorization"));
        // 缺失/过期/无效在本轮一律按匿名继续：统一的 401 JSON 契约（token_expired 等）由 a3 落地。
        if (verification instanceof TokenVerification.Valid valid) {
            SecurityContextHolder.getContext().setAuthentication(authentication(valid.principal()));
        }
        filterChain.doFilter(request, response);
    }

    private UsernamePasswordAuthenticationToken authentication(TokenPrincipal principal) {
        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.roles().stream()
                        .map(role -> new SimpleGrantedAuthority(
                                "ROLE_" + role.toUpperCase(Locale.ROOT).replace('-', '_')))
                        .toList());
    }
}
