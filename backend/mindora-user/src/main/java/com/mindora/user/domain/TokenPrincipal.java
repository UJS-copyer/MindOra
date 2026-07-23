package com.mindora.user.domain;

import java.util.Set;
import java.util.UUID;

public record TokenPrincipal(
        UUID userId,
        String email,
        Set<String> roles) {
}
