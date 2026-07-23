package com.mindora.user.infrastructure;

import com.mindora.user.domain.UserAccount;
import com.mindora.user.domain.TokenPrincipal;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class SimpleJwtTokenService implements TokenService {
    private final String secret;
    private final Clock clock;

    public SimpleJwtTokenService(String secret, Clock clock) {
        this.secret = secret;
        this.clock = clock;
    }

    @Override
    public String issue(UserAccount user) {
        String header = encode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = encode("{\"sub\":\"" + user.id() + "\",\"email\":\""
                + user.email() + "\",\"roles\":\"" + user.roles().stream()
                .map(SimpleJwtTokenService::normalizeRoleName)
                .sorted()
                .reduce((left, right) -> left + "," + right)
                .orElse("") + "\",\"iat\":\"" + Instant.now(clock) + "\"}");
        String signingInput = header + "." + payload;
        return "Bearer " + signingInput + "." + sign(signingInput);
    }

    @Override
    public Optional<TokenPrincipal> verify(String accessToken) {
        if (accessToken == null || !accessToken.startsWith("Bearer ")) {
            return Optional.empty();
        }
        String token = accessToken.substring("Bearer ".length());
        String[] segments = token.split("\\.", -1);
        if (segments.length != 3) {
            return Optional.empty();
        }
        String signingInput = segments[0] + "." + segments[1];
        if (!MessageDigest.isEqual(
                sign(signingInput).getBytes(StandardCharsets.UTF_8),
                segments[2].getBytes(StandardCharsets.UTF_8))) {
            return Optional.empty();
        }
        try {
            String payload = new String(
                    Base64.getUrlDecoder().decode(segments[1]),
                    StandardCharsets.UTF_8);
            Set<String> roles = new LinkedHashSet<>();
            String rolesValue = field(payload, "roles");
            if (!rolesValue.isBlank()) {
                Arrays.stream(rolesValue.split(","))
                        .map(SimpleJwtTokenService::normalizeRoleName)
                        .filter(role -> !role.isBlank())
                        .forEach(roles::add);
            }
            return Optional.of(new TokenPrincipal(
                    UUID.fromString(field(payload, "sub")),
                    field(payload, "email"),
                    Set.copyOf(roles)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static String normalizeRoleName(String role) {
        if (role == null) {
            return "";
        }
        return role.trim().toLowerCase().replace('-', '_');
    }

    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("Unable to sign access token", exception);
        }
    }

    private String field(String payload, String name) {
        String marker = "\"" + name + "\":\"";
        int start = payload.indexOf(marker);
        if (start < 0) {
            throw new IllegalArgumentException("Missing token field");
        }
        int valueStart = start + marker.length();
        int valueEnd = payload.indexOf('"', valueStart);
        if (valueEnd < 0) {
            throw new IllegalArgumentException("Malformed token field");
        }
        return payload.substring(valueStart, valueEnd);
    }
}
