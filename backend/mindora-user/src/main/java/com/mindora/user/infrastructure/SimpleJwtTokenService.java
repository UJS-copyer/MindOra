package com.mindora.user.infrastructure;

import com.mindora.user.domain.UserAccount;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
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
                + user.email() + "\",\"iat\":\"" + Instant.now(clock) + "\"}");
        String signingInput = header + "." + payload;
        return "Bearer " + signingInput + "." + sign(signingInput);
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
}
