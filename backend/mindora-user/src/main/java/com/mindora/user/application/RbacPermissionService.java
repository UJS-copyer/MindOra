package com.mindora.user.application;

import com.mindora.user.domain.RoleName;
import com.mindora.user.domain.UserAccount;

public class RbacPermissionService {
    public boolean canAccessAdmin(UserAccount user) {
        return user.roles().contains(RoleName.SUPER_ADMIN);
    }
}
