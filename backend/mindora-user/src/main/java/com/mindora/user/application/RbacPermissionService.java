package com.mindora.user.application;

import com.mindora.user.application.port.RbacPermissionGateway;
import com.mindora.user.domain.TokenPrincipal;
import com.mindora.user.domain.RoleName;
import com.mindora.user.domain.UserAccount;
import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class RbacPermissionService {
    private static final Map<String, Set<String>> DEFAULT_PERMISSIONS = Map.of(
            RoleName.SUPER_ADMIN.value(),
            Set.of(
                    "blog:read",
                    "blog:write",
                    "blog:publish",
                    "asset:manage",
                    "user:manage",
                    "role:manage",
                    "menu:manage"),
            RoleName.USER.value(),
            Set.of("blog:read"));

    private final RbacPermissionGateway gateway;

    public RbacPermissionService() {
        this(null);
    }

    public RbacPermissionService(RbacPermissionGateway gateway) {
        this.gateway = gateway;
    }

    public boolean canAccessAdmin(UserAccount user) {
        return user.roles().contains(RoleName.SUPER_ADMIN.value());
    }

    public boolean canAccessAdmin(TokenPrincipal principal) {
        return principal.roles().contains(RoleName.SUPER_ADMIN.value());
    }

    public Set<String> permissionsFor(TokenPrincipal principal) {
        return permissionsFor(principal.roles());
    }

    public Set<String> permissionsFor(UserAccount user) {
        return permissionsFor(user.roles());
    }

    public Set<String> permissionsFor(Set<String> roles) {
        if (roles.isEmpty()) {
            return Set.of();
        }

        if (gateway != null) {
            Set<String> permissions = gateway.permissionsForRoles(roles);
            if (!permissions.isEmpty()) {
                return permissions;
            }
        }

        return roles.stream()
                .map(this::normalizeRoleName)
                .flatMap(role -> DEFAULT_PERMISSIONS.getOrDefault(role, Set.of()).stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public Set<String> uiButtonsFor(Set<String> permissions) {
        Set<String> buttons = new LinkedHashSet<>();
        if (permissions.contains("blog:write")) {
            buttons.addAll(Set.of("add", "edit", "delete"));
        }
        if (permissions.contains("blog:publish")) {
            buttons.addAll(Set.of("publish", "unpublish"));
        }
        if (permissions.contains("asset:manage")) {
            buttons.add("upload");
        }
        if (permissions.contains("user:manage") || permissions.contains("role:manage")
                || permissions.contains("menu:manage")) {
            buttons.addAll(Set.of("add", "edit", "delete", "permission"));
        }
        return buttons;
    }

    public Set<String> roleCodesFor(Set<String> roles) {
        Set<String> codes = new LinkedHashSet<>();
        for (String role : roles) {
            String normalized = normalizeRoleName(role);
            if (RoleName.SUPER_ADMIN.value().equals(normalized)) {
                codes.add("R_SUPER");
                codes.add("R_ADMIN");
            } else if ("admin".equals(normalized)) {
                codes.add("R_ADMIN");
            } else if (RoleName.USER.value().equals(normalized)) {
                codes.add("R_USER");
            } else {
                codes.add("R_" + normalized.toUpperCase(Locale.ROOT).replace('-', '_'));
            }
        }
        return codes;
    }

    private String normalizeRoleName(String role) {
        return role == null ? "" : role.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }
}
