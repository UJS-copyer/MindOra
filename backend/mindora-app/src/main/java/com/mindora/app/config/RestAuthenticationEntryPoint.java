package com.mindora.app.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindora.common.web.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * 401 统一 JSON 体（design-12 §4.1）。
 *
 * <p>错误码由过滤器写入请求属性；没有属性说明是「未携带身份访问受保护路径」，记为 {@code token_missing}。
 * 默认的 Spring 入口点会返回空体，前端无法据此决定「续期」还是「回登录页」，因此整体替换。
 */
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {
    public static final String ERROR_CODE_ATTRIBUTE = "mindora.auth.errorCode";

    private static final Map<String, String> MESSAGES = Map.of(
            "token_missing", "Authentication required",
            "token_expired", "Access token expired",
            "token_invalid", "Access token is invalid",
            "token_revoked", "Access token has been revoked",
            "account_disabled", "Account is disabled");

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        String code = (String) request.getAttribute(ERROR_CODE_ATTRIBUTE);
        if (code == null || code.isBlank()) {
            code = "token_missing";
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), new ErrorResponse(
                code,
                MESSAGES.getOrDefault(code, "Authentication required"),
                null,
                traceId(request)));
    }

    static String traceId(HttpServletRequest request) {
        String traceId = request.getHeader("X-Trace-Id");
        return traceId == null || traceId.isBlank() ? "untracked" : traceId;
    }
}
