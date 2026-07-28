package com.mindora.user.application;

import com.mindora.user.application.port.AdminIdentityGateway;
import java.util.List;
import java.util.Set;

public class AdminIdentityService {
    private final AdminIdentityGateway gateway;

    public AdminIdentityService(AdminIdentityGateway gateway) {
        this.gateway = gateway;
    }

    public PageResult<UserRow> listUsers(UserSearch search) {
        return gateway.listUsers(search);
    }

    public UserRow createUser(UserRequest request) {
        return gateway.createUser(request);
    }

    public UserRow updateUser(String id, UserRequest request) {
        return gateway.updateUser(id, request);
    }

    public void deleteUser(String id) {
        gateway.deleteUser(id);
    }

    public PageResult<RoleRow> listRoles(RoleSearch search) {
        return gateway.listRoles(search);
    }

    public RoleRow createRole(RoleRequest request) {
        return gateway.createRole(request);
    }

    public RoleRow updateRole(String id, RoleRequest request) {
        return gateway.updateRole(id, request);
    }

    public void deleteRole(String id) {
        gateway.deleteRole(id);
    }

    public List<PermissionRow> listPermissions() {
        return gateway.listPermissions();
    }

    public Set<String> rolePermissions(String roleId) {
        return gateway.rolePermissions(roleId);
    }

    public Set<String> updateRolePermissions(String roleId, PermissionUpdateRequest request) {
        return gateway.updateRolePermissions(roleId, request);
    }

    public record PageResult<T>(
            List<T> records,
            int current,
            int size,
            long total) {
    }

    public record UserSearch(
            Integer current,
            Integer size,
            String userName,
            String userGender,
            String userPhone,
            String userEmail,
            String status) {
    }

    public record UserRequest(
            String userName,
            String userGender,
            String userPhone,
            String userEmail,
            String status,
            Set<String> userRoles,
            String password) {
    }

    public record UserRow(
            String id,
            String userName,
            String userGender,
            String userPhone,
            String userEmail,
            String status,
            Set<String> userRoles,
            String createTime,
            String updateTime) {
    }

    public record RoleSearch(
            Integer current,
            Integer size,
            String roleName,
            String roleCode,
            String description,
            Boolean enabled,
            String startTime,
            String endTime) {
    }

    public record RoleRequest(
            String roleName,
            String roleCode,
            String description,
            Boolean enabled,
            Set<String> permissions) {
    }

    public record RoleRow(
            String roleId,
            String roleName,
            String roleCode,
            String description,
            Boolean enabled,
            String createTime,
            Set<String> permissions) {
    }

    public record PermissionRow(
            String code,
            String description) {
    }

    public record PermissionUpdateRequest(
            Set<String> permissions) {
    }
}
