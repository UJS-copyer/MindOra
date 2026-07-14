package com.mindora.common.id;

import java.util.UUID;

public final class PublicIds {
    private PublicIds() {
    }

    public static String newId() {
        return toPublicId(UUID.randomUUID());
    }

    public static String toPublicId(UUID id) {
        return id == null ? null : id.toString().replace("-", "");
    }

    public static UUID toUuid(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("ID is required");
        }
        String value = id.trim();
        if (value.length() == 36) {
            return UUID.fromString(value);
        }
        if (value.length() != 32) {
            throw new IllegalArgumentException("ID must be 32 or 36 characters");
        }
        return UUID.fromString(
                value.substring(0, 8)
                        + "-"
                        + value.substring(8, 12)
                        + "-"
                        + value.substring(12, 16)
                        + "-"
                        + value.substring(16, 20)
                        + "-"
                        + value.substring(20));
    }
}
