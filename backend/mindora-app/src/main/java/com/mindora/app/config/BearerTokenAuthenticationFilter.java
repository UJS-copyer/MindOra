package com.mindora.app.config;

import com.mindora.user.application.AccessTokenValidator;
import com.mindora.user.domain.TokenPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class BearerTokenAuthenticationFilter extends OncePerRequestFilter {

    /**
     * 这四个端点必须「不因过期/无效的 access token 而失败」：
     * 登录、注册时浏览器可能还带着上一段会话的旧 token；刷新与登出正是修复/结束旧会话的动作。
     * 其余路径（含 /auth/me、管理端、公开端）遇到无效 token 一律 401，前端据此续期或回登录页。
     */
    private static final Set<String> TOKEN_TOLERANT_PATHS = Set.of(
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout");

    private final AccessTokenValidator validator;
    private final RestAuthenticationEntryPoint entryPoint;

    public BearerTokenAuthenticationFilter(
            AccessTokenValidator validator,
            RestAuthenticationEntryPoint entryPoint) {
        this.validator = validator;
        this.entryPoint = entryPoint;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        AccessTokenValidator.Outcome outcome = validator.validate(request.getHeader("Authorization"));
        if (outcome.authenticated()) {
            SecurityContextHolder.getContext().setAuthentication(authentication(outcome.principal()));
            filterChain.doFilter(request, response);
            return;
        }
        if (outcome.status() == AccessTokenValidator.Status.MISSING
                || TOKEN_TOLERANT_PATHS.contains(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }
        request.setAttribute(RestAuthenticationEntryPoint.ERROR_CODE_ATTRIBUTE, outcome.errorCode());
        entryPoint.commence(
                request,
                response,
                new InsufficientAuthenticationException(outcome.errorCode()));
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
