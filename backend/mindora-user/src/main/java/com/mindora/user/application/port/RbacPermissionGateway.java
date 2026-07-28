package com.mindora.user.application.port;

import java.util.Set;

public interface RbacPermissionGateway {
    Set<String> permissionsForRoles(Set<String> roles);
}
