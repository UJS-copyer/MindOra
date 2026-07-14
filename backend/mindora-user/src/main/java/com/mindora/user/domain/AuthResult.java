package com.mindora.user.domain;

public record AuthResult(
        UserAccount user,
        String accessToken) {
}
