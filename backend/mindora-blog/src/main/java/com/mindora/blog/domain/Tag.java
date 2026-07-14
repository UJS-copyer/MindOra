package com.mindora.blog.domain;

import java.time.Instant;
import java.util.UUID;

public record Tag(
        UUID id,
        String name,
        Instant createdAt,
        Instant updatedAt) {
}
