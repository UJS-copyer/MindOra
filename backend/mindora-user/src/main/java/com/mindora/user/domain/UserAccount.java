package com.mindora.user.domain;

import java.util.Set;
import java.util.UUID;

public record UserAccount(
        UUID id,
        String email,
        String passwordHash,
        Set<String> roles) {
}
