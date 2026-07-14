package com.mindora.user.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mindora.user.domain.RoleName;
import com.mindora.user.domain.UserAccount;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RbacPermissionTest {
    @Test
    void onlySuperAdminCanAccessAdminOperations() {
        RbacPermissionService permissionService = new RbacPermissionService();

        UserAccount user = new UserAccount(
                UUID.randomUUID(), "user@example.com", "hash", Set.of(RoleName.USER));
        UserAccount admin = new UserAccount(
                UUID.randomUUID(), "admin@example.com", "hash", Set.of(RoleName.SUPER_ADMIN));

        assertFalse(permissionService.canAccessAdmin(user));
        assertTrue(permissionService.canAccessAdmin(admin));
    }
}
