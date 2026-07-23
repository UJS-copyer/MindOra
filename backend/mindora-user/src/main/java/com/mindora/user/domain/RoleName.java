package com.mindora.user.domain;

public enum RoleName {
    SUPER_ADMIN("super_admin"),
    USER("user");

    private final String value;

    RoleName(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
