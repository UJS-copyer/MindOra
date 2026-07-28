package com.mindora.user.application;

import com.mindora.common.exception.BusinessException;
import com.mindora.user.application.port.AdminIdentityGateway;
import java.util.List;
import java.util.Set;

public class UnavailableAdminIdentityGateway implements AdminIdentityGateway {
    @Override
    public AdminIdentityService.PageResult<AdminIdentityService.UserRow> listUsers(
            AdminIdentityService.UserSearch search) {
        throw unavailable();
    }

    @Override
    public AdminIdentityService.UserRow createUser(AdminIdentityService.UserRequest request) {
        throw unavailable();
    }

    @Override
    public AdminIdentityService.UserRow updateUser(String id, AdminIdentityService.UserRequest request) {
        throw unavailable();
    }

    @Override
    public void deleteUser(String id) {
        throw unavailable();
    }

    @Override
    public AdminIdentityService.PageResult<AdminIdentityService.RoleRow> listRoles(
            AdminIdentityService.RoleSearch search) {
        throw unavailable();
    }

    @Override
    public AdminIdentityService.RoleRow createRole(AdminIdentityService.RoleRequest request) {
        throw unavailable();
    }

    @Override
    public AdminIdentityService.RoleRow updateRole(String id, AdminIdentityService.RoleRequest request) {
        throw unavailable();
    }

    @Override
    public void deleteRole(String id) {
        throw unavailable();
    }

    @Override
    public List<AdminIdentityService.PermissionRow> listPermissions() {
        throw unavailable();
    }

    @Override
    public Set<String> rolePermissions(String roleId) {
        throw unavailable();
    }

    @Override
    public Set<String> updateRolePermissions(
            String roleId,
            AdminIdentityService.PermissionUpdateRequest request) {
        throw unavailable();
    }

    private BusinessException unavailable() {
        return new BusinessException("admin_storage_unavailable", "Admin storage is unavailable");
    }
}
