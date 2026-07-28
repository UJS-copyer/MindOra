package com.mindora.user.application.port;

import com.mindora.user.application.AdminIdentityService.PageResult;
import com.mindora.user.application.AdminIdentityService.PermissionRow;
import com.mindora.user.application.AdminIdentityService.PermissionUpdateRequest;
import com.mindora.user.application.AdminIdentityService.RoleRequest;
import com.mindora.user.application.AdminIdentityService.RoleRow;
import com.mindora.user.application.AdminIdentityService.RoleSearch;
import com.mindora.user.application.AdminIdentityService.UserRequest;
import com.mindora.user.application.AdminIdentityService.UserRow;
import com.mindora.user.application.AdminIdentityService.UserSearch;
import java.util.List;
import java.util.Set;

public interface AdminIdentityGateway {
    PageResult<UserRow> listUsers(UserSearch search);

    UserRow createUser(UserRequest request);

    UserRow updateUser(String id, UserRequest request);

    void deleteUser(String id);

    PageResult<RoleRow> listRoles(RoleSearch search);

    RoleRow createRole(RoleRequest request);

    RoleRow updateRole(String id, RoleRequest request);

    void deleteRole(String id);

    List<PermissionRow> listPermissions();

    Set<String> rolePermissions(String roleId);

    Set<String> updateRolePermissions(String roleId, PermissionUpdateRequest request);
}
